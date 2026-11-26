import { useState } from 'react';
import { createOrder } from '../api';
import { formatPrice, formatWhen, newestFirst } from '../format';
import { describeError } from '../messages';
import ServiceColumn from './ServiceColumn';
import { EmptyState, Feedback, Field, SelectField, SubmitButton } from './ui';

const statusLabel = (status) => status.charAt(0) + status.slice(1).toLowerCase();

export default function OrdersColumn({ resource, users, products }) {
  const [userId, setUserId] = useState('');
  const [productId, setProductId] = useState('');
  const [quantity, setQuantity] = useState('1');
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState(null);

  const userNames = new Map(users.items.map((u) => [u.id, u.name]));
  const productNames = new Map(products.items.map((p) => [p.id, p.name]));

  const canOrder = users.items.length > 0 && products.items.length > 0;
  const product = products.items.find((p) => String(p.id) === productId);
  const qty = Number(quantity);
  const quantityIsValid = Number.isInteger(qty) && qty >= 1 && qty <= 100;
  const estimatedTotal = product && quantityIsValid ? Number(product.price) * qty : null;

  async function handleSubmit(event) {
    event.preventDefault();
    setBusy(true);
    setFeedback(null);
    try {
      await createOrder({
        userId: Number(userId),
        productId: Number(productId),
        quantity: qty,
      });
      setQuantity('1');
      setFeedback({ kind: 'ok', text: 'Order placed.' });
      await resource.reload();
    } catch (error) {
      setFeedback({ kind: 'error', text: describeError('order', error) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <ServiceColumn
      tint="orders"
      title="Orders"
      service="order-service"
      status={resource.status}
      count={resource.items.length}
      onRetry={resource.reload}
    >
      <p className="mb-4 text-sm text-muted">
        Placing an order makes order-service ask user-service and product-service to confirm the user
        and the price.
      </p>

      <form onSubmit={handleSubmit} className="space-y-3">
        <SelectField
          label="User"
          value={userId}
          onChange={(e) => setUserId(e.target.value)}
          required
          disabled={!canOrder}
        >
          <option value="">Choose a user</option>
          {users.items.map((u) => (
            <option key={u.id} value={u.id}>
              {u.name}
            </option>
          ))}
        </SelectField>
        <SelectField
          label="Product"
          value={productId}
          onChange={(e) => setProductId(e.target.value)}
          required
          disabled={!canOrder}
        >
          <option value="">Choose a product</option>
          {products.items.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </SelectField>
        <Field
          label="Quantity"
          type="number"
          inputMode="numeric"
          min="1"
          max="100"
          step="1"
          value={quantity}
          onChange={(e) => setQuantity(e.target.value)}
          required
          disabled={!canOrder}
          hint="From 1 to 100."
        />

        {estimatedTotal !== null && (
          <div aria-live="polite" className="pt-1">
            <div className="flex items-baseline gap-2 text-sm">
              <span>
                {qty} × {product.name}
              </span>
              <span aria-hidden="true" className="flex-1 border-b border-dotted border-muted/60" />
              <span className="font-display text-xl tabular-nums">{formatPrice(estimatedTotal)}</span>
            </div>
            <p className="mt-1 text-xs text-muted">Estimate. order-service calculates the final total.</p>
          </div>
        )}

        <SubmitButton tint="orders" busy={busy} disabled={!canOrder}>
          Place order
        </SubmitButton>
        {!canOrder && <p className="text-sm text-muted">Add a user and a product before placing an order.</p>}
        <Feedback feedback={feedback} />
      </form>

      {resource.items.length === 0 ? (
        <EmptyState>No orders yet. Place the first one with the form above.</EmptyState>
      ) : (
        <ul className="mt-5 divide-y divide-rule border-t border-rule">
          {newestFirst(resource.items).map((order) => (
            <li key={order.id} className="py-3">
              <div className="flex items-baseline justify-between gap-3">
                <p className="font-medium">Order {order.id}</p>
                <p className="font-display text-lg tabular-nums">{formatPrice(order.totalPrice)}</p>
              </div>
              <p className="text-sm">
                {userNames.get(order.userId) ?? `User ${order.userId}`} ordered {order.quantity} ×{' '}
                {productNames.get(order.productId) ?? `product ${order.productId}`}
              </p>
              <p className="text-xs text-muted">
                {statusLabel(order.status)}
                {formatWhen(order.createdAt) && `, ${formatWhen(order.createdAt)}`}
              </p>
            </li>
          ))}
        </ul>
      )}
    </ServiceColumn>
  );
}
