const price = new Intl.NumberFormat(undefined, {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const when = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
});

export const formatPrice = (value) => price.format(Number(value));

export function formatWhen(iso) {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? '' : when.format(date);
}

// Newest first (higher id = created later).
export const newestFirst = (items) => [...items].sort((a, b) => b.id - a.id);
