import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { VoiceInput } from './VoiceInput';

const mockStart = vi.fn();
const mockStop = vi.fn();

function createMockRecognition() {
  return {
    continuous: false,
    interimResults: false,
    lang: '',
    start: mockStart,
    stop: mockStop,
    onresult: null as ((e: unknown) => void) | null,
    onerror: null as ((e: unknown) => void) | null,
  };
}

describe('VoiceInput', () => {
  beforeEach(() => {
    mockStart.mockClear();
    mockStop.mockClear();
    const MockRecognition = function (this: ReturnType<typeof createMockRecognition>) {
      Object.assign(this, createMockRecognition());
    };
    (window as unknown as { SpeechRecognition: typeof MockRecognition }).SpeechRecognition =
      MockRecognition;
  });

  afterEach(() => {
    delete (window as unknown as { SpeechRecognition?: unknown }).SpeechRecognition;
  });

  it('renders microphone button when SpeechRecognition is available', () => {
    render(<VoiceInput onResult={vi.fn()} />);
    expect(screen.getByRole('button', { name: /Hold to speak/i })).toBeInTheDocument();
  });

  it('calls start on mouse down', () => {
    render(<VoiceInput onResult={vi.fn()} />);
    const btn = screen.getByRole('button', { name: /Hold to speak/i });
    fireEvent.mouseDown(btn);
    expect(mockStart).toHaveBeenCalled();
  });

  it('calls stop on mouse up after holding (after delay)', async () => {
    vi.useFakeTimers();
    render(<VoiceInput onResult={vi.fn()} />);
    const btn = screen.getByRole('button', { name: /Hold to speak/i });
    fireEvent.mouseDown(btn);
    fireEvent.mouseUp(btn);
    expect(mockStart).toHaveBeenCalled();
    expect(mockStop).not.toHaveBeenCalled();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(400);
    });
    expect(mockStop).toHaveBeenCalled();
    vi.useRealTimers();
  });

  it('cancels scheduled stop if user presses again within delay', async () => {
    vi.useFakeTimers();
    render(<VoiceInput onResult={vi.fn()} />);
    const btn = screen.getByRole('button', { name: /Hold to speak/i });
    fireEvent.mouseDown(btn);
    fireEvent.mouseUp(btn);
    fireEvent.mouseDown(btn);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(400);
    });
    expect(mockStop).not.toHaveBeenCalled();
    vi.useRealTimers();
  });

  it('disables button when disabled prop is true', () => {
    render(<VoiceInput onResult={vi.fn()} disabled />);
    const btn = screen.getByRole('button');
    expect(btn).toBeDisabled();
  });

  it('adds voice-input--active class when holding', () => {
    render(<VoiceInput onResult={vi.fn()} />);
    const btn = screen.getByRole('button', { name: /Hold to speak/i });
    fireEvent.mouseDown(btn);
    expect(btn).toHaveClass('voice-input--active');
  });
});
