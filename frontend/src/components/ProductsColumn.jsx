import { useState } from 'react';
import { createProduct } from '../api';
import { formatPrice, newestFirst } from '../format';
import { describeError } from '../messages';
import ServiceColumn from './ServiceColumn';
import { EmptyState, Feedback, Field, SubmitButton } from './ui';

export default function ProductsColumn({ resource }) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('');
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState(null);

  async function handleSubmit(event) {
    event.preventDefault();
    setBusy(true);
    setFeedback(null);
    try {
      await createProduct({
        name: name.trim(),
        description: description.trim() || null,
        price: Number(price),
      });
      setName('');
      setDescription('');
      setPrice('');
      setFeedback({ kind: 'ok', text: 'Product added.' });
      await resource.reload();
    } catch (error) {
      setFeedback({ kind: 'error', text: describeError('product', error) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <ServiceColumn
      tint="products"
      title="Products"
      service="product-service"
      status={resource.status}
      count={resource.items.length}
      onRetry={resource.reload}
    >
      <form onSubmit={handleSubmit} className="space-y-3">
        <Field
          label="Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
          maxLength={150}
          autoComplete="off"
        />
        <Field
          label="Description (optional)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={500}
          autoComplete="off"
        />
        <Field
          label="Price"
          type="number"
          inputMode="decimal"
          min="0.01"
          step="0.01"
          value={price}
          onChange={(e) => setPrice(e.target.value)}
          required
        />
        <SubmitButton tint="products" busy={busy}>
          Add product
        </SubmitButton>
        <Feedback feedback={feedback} />
      </form>

      {resource.items.length === 0 ? (
        <EmptyState>No products yet. Add the first one with the form above.</EmptyState>
      ) : (
        <ul className="mt-5 divide-y divide-rule border-t border-rule">
          {newestFirst(resource.items).map((product) => (
            <li key={product.id} className="flex items-baseline justify-between gap-3 py-3">
              <div className="min-w-0">
                <p className="truncate font-medium">{product.name}</p>
                {product.description && (
                  <p className="truncate text-sm text-muted">{product.description}</p>
                )}
              </div>
              <span className="font-display text-lg tabular-nums">{formatPrice(product.price)}</span>
            </li>
          ))}
        </ul>
      )}
    </ServiceColumn>
  );
}
