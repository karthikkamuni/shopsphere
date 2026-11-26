// One column per backend service. The header colour identifies the service and the
// status line shows whether the browser can currently reach it through /api/....

const HEADER_TINT = {
  users: 'bg-users text-white',
  products: 'bg-products text-ink',
  orders: 'bg-orders text-white',
};

const STATUS_WORDS = {
  loading: 'connecting',
  up: 'connected',
  down: 'unreachable',
};

const STATUS_DOT = {
  loading: 'border-2 border-current',
  up: 'bg-current',
  down: 'bg-danger ring-2 ring-white',
};

export default function ServiceColumn({ tint, title, service, status, count, onRetry, children }) {
  const headingId = `${tint}-heading`;
  return (
    <section
      aria-labelledby={headingId}
      className="overflow-hidden rounded-xl bg-white ring-1 ring-rule"
    >
      <header className={`px-5 pt-5 pb-4 ${HEADER_TINT[tint]}`}>
        <div className="flex items-baseline justify-between gap-3">
          <h2 id={headingId} className="font-display text-2xl font-semibold tracking-tight">
            {title}
          </h2>
          <span className="text-sm tabular-nums">{count} total</span>
        </div>
        <p className="mt-1 flex items-center gap-2 text-sm">
          <span aria-hidden="true" className={`size-2.5 shrink-0 rounded-full ${STATUS_DOT[status]}`} />
          <span>
            {service} is {STATUS_WORDS[status]}
          </span>
        </p>
      </header>

      <div className="p-5">
        {status === 'down' && (
          <div
            role="alert"
            className="mb-4 flex flex-wrap items-center justify-between gap-2 rounded-md border border-danger/30 bg-danger/5 px-3 py-2 text-sm text-danger"
          >
            <span>Could not reach {service}.</span>
            <button
              type="button"
              onClick={onRetry}
              className="min-h-9 rounded-md border border-danger px-3 font-semibold"
            >
              Try again
            </button>
          </div>
        )}
        {children}
      </div>
    </section>
  );
}
