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
