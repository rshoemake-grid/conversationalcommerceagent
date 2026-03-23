import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useChat } from './useChat'
import * as chatApi from '../api/chatApi'

vi.mock('../api/chatApi', () => ({
  sendChatMessage: vi.fn(),
}))

const { mockSpeak } = vi.hoisted(() => ({ mockSpeak: vi.fn() }))
vi.mock('./useVoiceOutput', () => ({
  useVoiceOutput: () => ({
    speak: mockSpeak,
    stop: vi.fn(),
    isSpeaking: false,
  }),
}))

describe('useChat', () => {
  beforeEach(() => {
    vi.mocked(chatApi.sendChatMessage).mockReset()
    mockSpeak.mockClear()
    vi.stubGlobal('crypto', { randomUUID: () => 'test-uuid' })
  })

  it('returns initial state', () => {
    const { result } = renderHook(() => useChat())
    expect(result.current.mode).toBe('convo_commerce')
    expect(result.current.messages).toEqual([])
    expect(result.current.input).toBe('')
    expect(result.current.loading).toBe(false)
  })

  it('updates input when setInput is called', () => {
    const { result } = renderHook(() => useChat())
    act(() => {
      result.current.setInput('hello')
    })
    expect(result.current.input).toBe('hello')
  })

  it('updates mode when setMode is called', () => {
    const { result } = renderHook(() => useChat())
    act(() => {
      result.current.setMode('adk_orchestrator')
    })
    expect(result.current.mode).toBe('adk_orchestrator')
  })

  it('handleSend adds user message and calls API', async () => {
    vi.mocked(chatApi.sendChatMessage).mockResolvedValue({
      text: 'Hi!',
      conversationId: 'c1',
    })

    const { result } = renderHook(() => useChat())
    act(() => {
      result.current.setInput('hi')
    })
    await act(async () => {
      result.current.handleSend()
    })

    expect(result.current.messages).toHaveLength(2)
    expect(result.current.messages[0].content).toBe('hi')
    expect(result.current.messages[0].role).toBe('user')
    expect(result.current.messages[1].content).toBe('Hi!')
    expect(chatApi.sendChatMessage).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'hi', mode: 'convo_commerce' })
    )
  })

  it('speaks assistant response when voice output is enabled', async () => {
    vi.mocked(chatApi.sendChatMessage).mockResolvedValue({
      text: 'Here are some products!',
      conversationId: 'c1',
    })

    const { result } = renderHook(() => useChat())
    act(() => {
      result.current.setVoiceOutputEnabled(true)
    })
    act(() => result.current.setInput('hi'))
    await act(async () => result.current.handleSend())

    expect(mockSpeak).toHaveBeenCalledWith('Here are some products!')
  })

  it('handleVoiceResult ignores "Attached" and "attach" as false positives', () => {
    const { result } = renderHook(() => useChat())
    act(() => result.current.handleVoiceResult('Attached'))
    expect(result.current.messages).toHaveLength(0)
    expect(chatApi.sendChatMessage).not.toHaveBeenCalled()
    act(() => result.current.handleVoiceResult('attach'))
    expect(result.current.messages).toHaveLength(0)
    expect(chatApi.sendChatMessage).not.toHaveBeenCalled()
  })

  it('handleVoiceResult sends transcript to agent automatically', async () => {
    vi.mocked(chatApi.sendChatMessage).mockResolvedValue({
      text: 'Here are some options',
      conversationId: 'c1',
    })

    const { result } = renderHook(() => useChat())
    await act(async () => {
      result.current.handleVoiceResult('search for shoes')
    })

    expect(result.current.messages).toHaveLength(2)
    expect(result.current.messages[0].content).toBe('search for shoes')
    expect(result.current.messages[0].role).toBe('user')
    expect(result.current.messages[1].content).toBe('Here are some options')
    expect(chatApi.sendChatMessage).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'search for shoes', mode: 'convo_commerce' })
    )
  })

  it('handleDismissError removes error message', async () => {
    vi.mocked(chatApi.sendChatMessage).mockRejectedValue(new Error('Failed'))

    const { result } = renderHook(() => useChat())
    act(() => result.current.setInput('hi'))
    await act(async () => result.current.handleSend())

    expect(result.current.messages).toHaveLength(2)
    const errorId = result.current.messages[1].id

    act(() => {
      result.current.handleDismissError(errorId)
    })

    expect(result.current.messages).toHaveLength(1)
    expect(result.current.messages[0].role).toBe('user')
  })

  it('shows no-results message when API returns empty products and Searching placeholder', async () => {
    vi.mocked(chatApi.sendChatMessage).mockResolvedValue({
      text: 'Searching for: corn flakes',
      conversationId: 'c1',
      refinedQuery: 'corn flakes',
      products: [],
    })

    const { result } = renderHook(() => useChat())
    act(() => result.current.setInput('corn flakes'))
    await act(async () => result.current.handleSend())

    expect(result.current.messages).toHaveLength(2)
    expect(result.current.messages[1].content).toBe('No products found.')
  })

  it('extracts suggestedAnswers from rawResponse when backend does not provide them', async () => {
    const rawWithSuggested = JSON.stringify({
      conversationalFilteringResult: {
        followupQuestion: 'What type?',
        suggestedAnswers: [
          { productAttributeValue: { value: 'Balloons' } },
          { productAttributeValue: { value: 'Streamers' } },
        ],
      },
    });
    vi.mocked(chatApi.sendChatMessage).mockResolvedValue({
      text: 'What type of decoration?',
      conversationId: 'c1',
      rawResponse: rawWithSuggested,
    });

    const { result } = renderHook(() => useChat())
    act(() => result.current.setInput('princess decorations'))
    await act(async () => result.current.handleSend())

    expect(result.current.messages).toHaveLength(2)
    const assistantMsg = result.current.messages[1]
    expect(assistantMsg.suggestedAnswers).toEqual([
      { displayText: 'Balloons', value: 'Balloons' },
      { displayText: 'Streamers', value: 'Streamers' },
    ])
  })

  it('applies title case to brand-like codes when extracting from rawResponse', async () => {
    const rawWithBrands = JSON.stringify({
      conversationalFilteringResult: {
        suggestedAnswers: [
          { productAttributeValue: { value: 'NIKE' } },
          { productAttributeValue: { value: 'ADIDAS' } },
        ],
      },
    });
    vi.mocked(chatApi.sendChatMessage).mockResolvedValue({
      text: 'Which brand?',
      conversationId: 'c1',
      rawResponse: rawWithBrands,
    });

    const { result } = renderHook(() => useChat())
    act(() => result.current.setInput('shoes'))
    await act(async () => result.current.handleSend())

    const assistantMsg = result.current.messages[1]
    expect(assistantMsg.suggestedAnswers).toEqual([
      { displayText: 'Nike', value: 'NIKE' },
      { displayText: 'Adidas', value: 'ADIDAS' },
    ])
  })

  it('filters out suggested answers that were tried and resulted in same assistant message', async () => {
    const { result } = renderHook(() => useChat())

    // First: assistant asks "Which brand?" with Nike, Adidas, Puma
    vi.mocked(chatApi.sendChatMessage)
      .mockResolvedValueOnce({
        text: 'Which brand?',
        conversationId: 'c1',
        suggestedAnswers: [
          { displayText: 'Nike', value: 'NIKE' },
          { displayText: 'Adidas', value: 'ADIDAS' },
          { displayText: 'Puma', value: 'PUMA' },
        ],
      })
      .mockResolvedValueOnce({
        text: 'Which brand?',
        conversationId: 'c1',
        suggestedAnswers: [
          { displayText: 'Nike', value: 'NIKE' },
          { displayText: 'Adidas', value: 'ADIDAS' },
          { displayText: 'Puma', value: 'PUMA' },
        ],
      })
      .mockResolvedValueOnce({
        text: 'Which brand?',
        conversationId: 'c1',
        suggestedAnswers: [
          { displayText: 'Nike', value: 'NIKE' },
          { displayText: 'Adidas', value: 'ADIDAS' },
          { displayText: 'Puma', value: 'PUMA' },
        ],
      })

    act(() => result.current.setInput('shoes'))
    await act(async () => result.current.handleSend())

    expect(result.current.messages).toHaveLength(2)
    expect(result.current.messages[1].suggestedAnswers).toHaveLength(3)

    act(() => result.current.handleSuggestedAnswer('NIKE'))
    await act(async () => {})

    expect(result.current.messages).toHaveLength(4)
    const secondAssistant = result.current.messages[3]
    expect(secondAssistant.content).toBe('Which brand?')
    expect(secondAssistant.suggestedAnswers).toHaveLength(2)
    expect(secondAssistant.suggestedAnswers?.map((s) => s.value)).toEqual(['ADIDAS', 'PUMA'])

    act(() => result.current.handleSuggestedAnswer('ADIDAS'))
    await act(async () => {})

    expect(result.current.messages).toHaveLength(6)
    const thirdAssistant = result.current.messages[5]
    expect(thirdAssistant.content).toBe('Which brand?')
    expect(thirdAssistant.suggestedAnswers).toHaveLength(1)
    expect(thirdAssistant.suggestedAnswers?.map((s) => s.value)).toEqual(['PUMA'])
  })

  it('filters out suggested answer when it leads to no products found', async () => {
    const { result } = renderHook(() => useChat())

    vi.mocked(chatApi.sendChatMessage)
      .mockResolvedValueOnce({
        text: 'Which brand would you prefer?',
        conversationId: 'c1',
        suggestedAnswers: [
          { displayText: 'BHB/NPM', value: 'BHB/NPM' },
          { displayText: 'CAB', value: 'CAB' },
        ],
      })
      .mockResolvedValueOnce({
        text: 'Which brand would you prefer?\n\nNo products found.',
        conversationId: 'c1',
        products: [],
        suggestedAnswers: [
          { displayText: 'BHB/NPM', value: 'BHB/NPM' },
          { displayText: 'CAB', value: 'CAB' },
        ],
      })

    act(() => result.current.setInput('beef'))
    await act(async () => result.current.handleSend())

    expect(result.current.messages).toHaveLength(2)
    expect(result.current.messages[1].suggestedAnswers).toHaveLength(2)

    act(() => result.current.handleSuggestedAnswer('BHB/NPM'))
    await act(async () => {})

    expect(result.current.messages).toHaveLength(4)
    const secondAssistant = result.current.messages[3]
    expect(secondAssistant.content).toContain('No products found')
    expect(secondAssistant.suggestedAnswers).toHaveLength(1)
    expect(secondAssistant.suggestedAnswers?.map((s) => s.value)).toEqual(['CAB'])
  })

  it('startNewConversation clears messages, conversationId, input, and pending image', async () => {
    vi.mocked(chatApi.sendChatMessage).mockResolvedValue({
      text: 'Hi!',
      conversationId: 'c1',
    })

    const { result } = renderHook(() => useChat())
    act(() => result.current.setInput('hello'))
    await act(async () => result.current.handleSend())

    expect(result.current.messages).toHaveLength(2)
    expect(result.current.input).toBe('')

    act(() => {
      result.current.setInput('follow-up')
      result.current.startNewConversation()
    })

    expect(result.current.messages).toEqual([])
    expect(result.current.input).toBe('')
  })
})
