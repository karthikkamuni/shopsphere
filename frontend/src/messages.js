// The services return bare status codes (no message body), so the UI explains each
// failure in plain words. Anything unknown is treated as "service not reachable".

const SERVICE = {
  user: 'user-service',
  product: 'product-service',
  order: 'order-service',
};

const KNOWN = {
  user: {
    400: 'Enter a name and a valid email address.',
    409: 'That email address is already registered.',
  },
  product: {
    400: 'Enter a name and a price above 0.',
  },
  order: {
    400: 'Choose an existing user and product, and a quantity from 1 to 100.',
    503: 'order-service could not reach user-service or product-service. Check that both are running.',
  },
};

export function describeError(kind, error) {
  const known = KNOWN[kind]?.[error?.status];
  if (known) return known;
  return `Could not reach ${SERVICE[kind]}. Check that it is running, then try again.`;
}
