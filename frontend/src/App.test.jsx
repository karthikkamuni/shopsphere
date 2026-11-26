import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App.jsx';

const json = (data, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });

// A tiny in-memory stand-in for the three backend services.
function createFakeApi(seed = {}) {
  const db = { users: [], products: [], orders: [], ...seed };
  const failures = new Map(); // e.g. 'POST /api/users' -> 409, or 'GET /api/products' -> 'network'
  const calls = [];

  const fetchMock = vi.fn(async (url, options = {}) => {
    const method = options.method ?? 'GET';
    const body = options.body ? JSON.parse(options.body) : undefined;
    calls.push({ method, url, body });

    const failure = failures.get(`${method} ${url}`);
    if (failure === 'network') throw new TypeError('Failed to fetch');
    if (failure) return new Response(null, { status: failure });

    const collection = url.replace('/api/', '');
    if (method === 'GET') return json(db[collection]);

    const item = { id: db[collection].length + 1, createdAt: '2026-11-24T10:00:00Z', ...body };
    if (collection === 'orders') {
      const product = db.products.find((p) => p.id === body.productId);
      item.unitPrice = product.price;
      item.totalPrice = Number((product.price * body.quantity).toFixed(2));
      item.status = 'CREATED';
    }
    db[collection].push(item);
    return json(item, 201);
  });

  return { db, failures, calls, fetchMock };
}

const ASHA = { id: 1, name: 'Asha Rao', email: 'asha.rao@example.com' };
const KEYBOARD = { id: 1, name: 'Mechanical Keyboard', description: 'Tenkeyless', price: 49.99 };
const ORDER = {
  id: 1,
  userId: 1,
  productId: 1,
  quantity: 2,
  unitPrice: 49.99,
  totalPrice: 99.98,
  status: 'CREATED',
  createdAt: '2026-11-24T10:00:00Z',
};

let api;
const region = (name) => screen.getByRole('region', { name });

function renderApp(seed) {
  api = createFakeApi(seed);
  vi.stubGlobal('fetch', api.fetchMock);
  render(<App />);
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('ShopSphere UI', () => {
  it('loads existing data from all three services', async () => {
    renderApp({ users: [ASHA], products: [KEYBOARD], orders: [ORDER] });

    expect(await screen.findAllByText(/is connected/)).toHaveLength(3);
    expect(within(region('Users')).getByText('asha.rao@example.com')).toBeInTheDocument();
    expect(within(region('Products')).getByText('Mechanical Keyboard')).toBeInTheDocument();
    expect(within(region('Orders')).getByText('Asha Rao ordered 2 × Mechanical Keyboard')).toBeInTheDocument();
    expect(within(region('Orders')).getByText('99.98')).toBeInTheDocument();
  });

  it('adds a user and shows it in the list', async () => {
    const user = userEvent.setup();
    renderApp();
    await screen.findAllByText(/is connected/);

    const users = within(region('Users'));
    await user.type(users.getByLabelText('Name'), 'Asha Rao');
    await user.type(users.getByLabelText('Email'), 'asha.rao@example.com');
    await user.click(users.getByRole('button', { name: 'Add user' }));

    expect(await users.findByText('User added.')).toBeInTheDocument();
    expect(await users.findByText('asha.rao@example.com')).toBeInTheDocument();
    expect(api.calls).toContainEqual({
      method: 'POST',
      url: '/api/users',
      body: { name: 'Asha Rao', email: 'asha.rao@example.com' },
    });
  });

  it('explains a duplicate email in plain words', async () => {
    const user = userEvent.setup();
    renderApp({ users: [ASHA] });
    await screen.findAllByText(/is connected/);
    api.failures.set('POST /api/users', 409);

    const users = within(region('Users'));
    await user.type(users.getByLabelText('Name'), 'Asha Again');
    await user.type(users.getByLabelText('Email'), 'asha.rao@example.com');
    await user.click(users.getByRole('button', { name: 'Add user' }));

    expect(await users.findByRole('alert')).toHaveTextContent('That email address is already registered.');
  });

  it('adds a product with a price', async () => {
    const user = userEvent.setup();
    renderApp();
    await screen.findAllByText(/is connected/);

    const products = within(region('Products'));
    await user.type(products.getByLabelText('Name'), 'Mechanical Keyboard');
    await user.type(products.getByLabelText('Price'), '49.99');
    await user.click(products.getByRole('button', { name: 'Add product' }));

    expect(await products.findByText('Product added.')).toBeInTheDocument();
    expect(await products.findByText('49.99')).toBeInTheDocument();
    expect(api.calls).toContainEqual({
      method: 'POST',
      url: '/api/products',
      body: { name: 'Mechanical Keyboard', description: null, price: 49.99 },
    });
  });

  it('previews the total and places an order', async () => {
    const user = userEvent.setup();
    renderApp({ users: [ASHA], products: [KEYBOARD] });
    await screen.findAllByText(/is connected/);

    const orders = within(region('Orders'));
    await user.selectOptions(orders.getByLabelText('User'), 'Asha Rao');
    await user.selectOptions(orders.getByLabelText('Product'), 'Mechanical Keyboard');
    await user.clear(orders.getByLabelText('Quantity'));
    await user.type(orders.getByLabelText('Quantity'), '3');

    expect(orders.getByText('149.97')).toBeInTheDocument();

    await user.click(orders.getByRole('button', { name: 'Place order' }));

    expect(await orders.findByText('Order placed.')).toBeInTheDocument();
    expect(await orders.findByText('Asha Rao ordered 3 × Mechanical Keyboard')).toBeInTheDocument();
    expect(api.calls).toContainEqual({
      method: 'POST',
      url: '/api/orders',
      body: { userId: 1, productId: 1, quantity: 3 },
    });
  });

  it('asks for a user and a product before an order can be placed', async () => {
    renderApp();
    await screen.findAllByText(/is connected/);

    const orders = within(region('Orders'));
    expect(orders.getByText('Add a user and a product before placing an order.')).toBeInTheDocument();
    expect(orders.getByRole('button', { name: 'Place order' })).toBeDisabled();
  });

  it('shows an unreachable service and recovers when you try again', async () => {
    const user = userEvent.setup();
    api = createFakeApi({ products: [KEYBOARD] });
    api.failures.set('GET /api/products', 'network');
    vi.stubGlobal('fetch', api.fetchMock);
    render(<App />);

    expect(await screen.findByText('product-service is unreachable')).toBeInTheDocument();
    expect(within(region('Products')).getByRole('alert')).toHaveTextContent('Could not reach product-service.');
    expect(await screen.findAllByText(/is connected/)).toHaveLength(2);

    api.failures.delete('GET /api/products');
    await user.click(within(region('Products')).getByRole('button', { name: 'Try again' }));

    expect(await screen.findAllByText(/is connected/)).toHaveLength(3);
    expect(within(region('Products')).getByText('Mechanical Keyboard')).toBeInTheDocument();
  });
});
