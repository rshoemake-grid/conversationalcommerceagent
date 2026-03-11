import type { OrchestrationMode } from '../api/types';

interface ModeSelectorProps {
  value: OrchestrationMode;
  onChange: (mode: OrchestrationMode) => void;
  disabled?: boolean;
}

export function ModeSelector({ value, onChange, disabled }: ModeSelectorProps) {
  return (
    <div className="mode-selector">
      <label>Orchestration mode:</label>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value as OrchestrationMode)}
        disabled={disabled}
      >
        <option value="convo_commerce">
          Approach A: Convo Commerce as Orchestrator
        </option>
        <option value="adk_orchestrator">
          Approach B: ADK Agent as Orchestrator
        </option>
      </select>
    </div>
  );
}
