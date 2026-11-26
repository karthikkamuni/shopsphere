import { useId } from 'react';

const BUTTON_TINT = {
  users: 'bg-users text-white',
  products: 'bg-products text-ink',
  orders: 'bg-orders text-white',
};

const controlClass =
  'mt-1 block min-h-10 w-full rounded-md border border-rule bg-white px-3 py-2 text-base text-ink placeholder:text-muted/70';

export function Field({ label, hint, ...inputProps }) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <input id={id} className={controlClass} {...inputProps} />
      {hint && <p className="mt-1 text-xs text-muted">{hint}</p>}
    </div>
  );
}

export function SelectField({ label, children, ...selectProps }) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <select id={id} className={controlClass} {...selectProps}>
        {children}
      </select>
    </div>
  );
}

export function SubmitButton({ tint, busy, disabled, children }) {
  return (
    <button
      type="submit"
      disabled={busy || disabled}
      className={`min-h-10 rounded-md px-4 text-base font-semibold ${BUTTON_TINT[tint]} disabled:cursor-not-allowed disabled:opacity-50`}
    >
      {children}
    </button>
  );
}

export function Feedback({ feedback }) {
  if (!feedback) return null;
  const isError = feedback.kind === 'error';
  return (
    <p
      role={isError ? 'alert' : 'status'}
      className={`text-sm font-medium ${isError ? 'text-danger' : 'text-ok'}`}
    >
      {feedback.text}
    </p>
  );
}

export function EmptyState({ children }) {
  return <p className="py-6 text-sm text-muted">{children}</p>;
}
