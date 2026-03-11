import { describe, it, expect, vi, beforeEach } from 'vitest'
import { sendChatMessage } from './chatApi'
import type { ChatRequest } from './types'

describe('chatApi', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('sends POST to /api/chat with request body', async () => {
    const mockFetch = vi.mocked(fetch)
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ text: 'Hello', conversationId: 'c1' }),
    } as Response)

    const result = await sendChatMessage({
      mode: 'convo_commerce',
      message: 'hi',
    })

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/chat',
      expect.objectContaining({
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mode: 'convo_commerce', message: 'hi' }),
      })
    )
    expect(result.text).toBe('Hello')
    expect(result.conversationId).toBe('c1')
  })

  it('throws on non-ok response', async () => {
    vi.mocked(fetch).mockResolvedValue({
      ok: false,
      status: 500,
      text: () => Promise.resolve('Server error'),
    } as Response)

    await expect(sendChatMessage({ mode: 'adk_orchestrator', message: 'x' }))
      .rejects.toThrow()
  })
})
