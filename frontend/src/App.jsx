import { useCallback, useEffect, useState } from 'react';
import { listOrders, listProducts, listUsers } from './api';
import OrdersColumn from './components/OrdersColumn';
import ProductsColumn from './components/ProductsColumn';
import UsersColumn from './components/UsersColumn';

// Loads one list from one service and remembers whether that service answered.
// status: 'loading' (first request), 'up' (last request worked), 'down' (last request failed).
function useResource(fetcher) {
  const [state, setState] = useState({ status: 'loading', items: [] });

  const reload = useCallback(async () => {
    try {
      const items = await fetcher();
      setState({ status: 'up', items });
    } catch {
      setState((previous) => ({ status: 'down', items: previous.items }));
    }
  }, [fetcher]);

  useEffect(() => {
    reload();
  }, [reload]);

  return { ...state, reload };
}

function Sphere() {
  return (
    <svg viewBox="0 0 32 32" fill="none" className="size-9 shrink-0" aria-hidden="true">
      <circle cx="16" cy="16" r="14" fill="#172229" />
      <ellipse cx="16" cy="16" rx="6" ry="14" stroke="#e3a008" strokeWidth="1.8" />
      <path d="M2.5 16h27" stroke="#0b7a75" strokeWidth="1.8" />
      <path
        d="M5 9.5c7 3 15 3 22 0M5 22.5c7-3 15-3 22 0"
        stroke="#3b4cca"
        strokeWidth="1.8"
        strokeLinecap="round"
      />
    </svg>
  );
}

export default function App() {
  const users = useResource(listUsers);
  const products = useResource(listProducts);
  const orders = useResource(listOrders);

  return (
    <>
      <header className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-4 gap-y-2 px-5 pt-8 pb-6">
        <Sphere />
        <h1 className="font-display text-3xl font-bold tracking-tight">ShopSphere</h1>
        <p className="text-muted">
          Add users and products, then place an order. Each column talks to its own service.
        </p>
      </header>

      <main className="mx-auto grid max-w-7xl items-start gap-6 px-5 pb-12 lg:grid-cols-3">
        <UsersColumn resource={users} />
        <ProductsColumn resource={products} />
        <OrdersColumn resource={orders} users={users} products={products} />
      </main>
    </>
  );
}
