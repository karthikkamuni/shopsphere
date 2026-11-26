import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App.jsx';

const json = (data, status = 200) => new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });
const product = { id: 7, name: 'Studio headphones', description: 'Warm, clear sound.', price: 149.5, category: 'Electronics', imageUrl: '', stock: 8, available: true, active: true };
const user = { id: 4, name: 'Mira Sen', email: 'mira@example.com', role: 'USER', blocked: false };
const admin = { id: 1, name: 'Shop Admin', email: 'admin@example.com', role: 'ADMIN', blocked: false };

function fakeFetch() {
  const calls = [];
  let cart = { items: [], total: 0, itemCount: 0 };
  let orders = [];
  const fetchMock = vi.fn(async (url, options = {}) => {
    const method = options.method || 'GET'; const body = options.body ? JSON.parse(options.body) : undefined;
    calls.push({ url, method, body, headers: options.headers });
    if (url.startsWith('/api/products/page')) return json({ content: [product], number: 0, size: 12, totalElements: 1, totalPages: 1 });
    if (url.startsWith('/api/products?') || url === '/api/products') return json([product]);
    if (url === '/api/products/7') return json(product);
    if (url === '/api/auth/login') return json({ accessToken: 'jwt-user', tokenType: 'Bearer', expiresIn: 3600, user });
    if (url === '/api/auth/register') return json(user, 201);
    if (url === '/api/users/me' && method === 'GET') return json(user);
    if (url === '/api/users/me' && method === 'PUT') return json({ ...user, ...body });
    if (url === '/api/cart' && method === 'GET') return json(cart);
    if (url === '/api/cart/items' && method === 'POST') { cart = { items: [{ productId: 7, product, quantity: body.quantity, unitPrice: product.price, subtotal: product.price * body.quantity, available: true, stock: product.stock }], total: product.price * body.quantity, itemCount: body.quantity }; return json(cart, 201); }
    if (url === '/api/orders/checkout') { expect(options.headers['Idempotency-Key']).toBeTruthy(); const order = { id: 31, userId: 4, productId: 7, productName: product.name, quantity: 1, unitPrice: product.price, totalPrice: product.price, status: 'PLACED', createdAt: '2026-11-26T10:00:00Z', items: [{ productId: 7, productName: product.name, unitPrice: product.price, quantity: 1, subtotal: product.price }] }; orders.push(order); cart = { items: [], total: 0, itemCount: 0 }; return json(order, 201); }
    if (url === '/api/orders/31') return json(orders[0] || { id: 31, userId: 4, totalPrice: 149.5, status: 'PLACED', items: [{ productId: 7, productName: product.name, unitPrice: 149.5, quantity: 1, subtotal: 149.5 }] });
    if (url.startsWith('/api/orders?')) return json({ content: orders, number: 0, totalPages: 1 });
    if (url === '/api/admin/dashboard') return json({ totalOrders: 12, pendingOrders: 3, deliveredOrders: 8, cancelledOrders: 1, revenue: 920.5, recentOrders: [] });
    if (url === '/api/users?q=' || url === '/api/users') return json([{ ...user, createdAt: '2026-11-20T12:00:00Z' }]);
    if (url === '/api/users/4') return json({ ...user, createdAt: '2026-11-20T12:00:00Z' });
    if (url === '/api/products/admin/low-stock?threshold=5') return json([]);
    if (url === '/api/products/7/availability' && method === 'PATCH') return json({ ...product, available: body.available });
    if (url === '/api/auth/logout') return new Response(null, { status: 204 });
    return json({ message: `Unexpected request ${method} ${url}` }, 404);
  });
  return { calls, fetchMock };
}

function startAt(path) { window.history.replaceState({}, '', path); }
function renderApp(path = '/') { startAt(path); const api = fakeFetch(); vi.stubGlobal('fetch', api.fetchMock); render(<App />); return api; }
beforeEach(() => { localStorage.clear(); startAt('/'); });
afterEach(() => { vi.unstubAllGlobals(); localStorage.clear(); });

describe('ShopSphere customer experience', () => {
  it('browses and filters the real product catalogue contract', async () => {
    const api = renderApp('/catalog?category=Electronics');
    expect(await screen.findByRole('heading', { name: /shop thoughtfully/i })).toBeInTheDocument();
    expect(screen.getByText('Studio headphones')).toBeInTheDocument();
    expect(api.calls.some((call) => call.url.includes('/api/products/page?') && call.url.includes('category=Electronics'))).toBe(true);
  });

  it('registers and logs in through the user-service contract', async () => {
    const user = userEvent.setup(); const api = renderApp('/register');
    await user.type(screen.getByLabelText('Your name'), 'Mira Sen');
    await user.type(screen.getByLabelText('Email address'), 'mira@example.com');
    await user.type(screen.getByLabelText('Password'), 'long-password-value');
    await user.click(screen.getByRole('button', { name: /create account/i }));
    await screen.findByRole('heading', { name: /sign in/i });
    expect(api.calls.find((call) => call.url === '/api/auth/register')?.body).toEqual({ name: 'Mira Sen', email: 'mira@example.com', password: 'long-password-value' });
    cleanup(); localStorage.clear();
    const loginApi = renderApp('/login');
    await user.type(screen.getByLabelText('Email address'), 'mira@example.com');
    await user.type(screen.getByLabelText('Password'), 'long-password-value');
    await user.click(screen.getByRole('button', { name: /^sign in/i }));
    await waitFor(() => expect(loginApi.calls.some((call) => call.url === '/api/auth/login')).toBe(true));
    expect(await screen.findByRole('heading', { level: 1, name: /find your/i })).toBeInTheDocument();
    expect(localStorage.getItem('shopsphere.jwt')).toBe('jwt-user');
  });

  it('adds a cart item, checks out with an idempotency key, then opens order confirmation', async () => {
    localStorage.setItem('shopsphere.jwt', 'jwt-user');
    const calls = [];
    let cart = { items: [], total: 0, itemCount: 0 };
    const fetchMock = vi.fn(async (url, options = {}) => {
      const method = options.method || 'GET'; const body = options.body ? JSON.parse(options.body) : undefined; calls.push({url,method,body,headers:options.headers});
      if (url === '/api/users/me') return json(user);
      if (url === '/api/cart') return json(cart);
      if (url === '/api/products/7') return json(product);
      if (url === '/api/cart/items') { cart = { items: [{ productId:7,product,quantity:body.quantity,unitPrice:product.price,subtotal:product.price*body.quantity,available:true,stock:8 }], total:product.price*body.quantity,itemCount:body.quantity }; return json(cart,201); }
      if (url === '/api/orders/checkout') { return json({id:31,userId:4,productId:7,quantity:1,unitPrice:149.5,totalPrice:149.5,status:'PLACED',createdAt:'2026-11-26T10:00:00Z',items:[{productId:7,productName:product.name,unitPrice:149.5,quantity:1,subtotal:149.5}]},201); }
      if (url === '/api/orders/31') return json({id:31,userId:4,productId:7,quantity:1,unitPrice:149.5,totalPrice:149.5,status:'PLACED',items:[{productId:7,productName:product.name,unitPrice:149.5,quantity:1,subtotal:149.5}]});
      return json([],200);
    });
    vi.stubGlobal('fetch',fetchMock); startAt('/products/7'); const user= userEvent.setup(); render(<App />);
    await user.click(await screen.findByRole('button',{name:/add to bag/i}));
    await screen.findByRole('heading',{name:/the good things/i});
    await user.click(screen.getByRole('button',{name:/continue to checkout/i}));
    await screen.findByRole('heading',{name:/review & place order/i});
    await user.click(screen.getByRole('button',{name:/place order/i}));
    expect(await screen.findByRole('heading',{name:/your order is confirmed/i})).toBeInTheDocument();
    expect(calls.find(c=>c.url==='/api/orders/checkout')?.headers['Idempotency-Key']).toMatch(/[0-9a-f-]{36}/i);
  });

  it('changes cart quantities and removes cart lines', async () => {
    localStorage.setItem('shopsphere.jwt','jwt-user');let cart={items:[{productId:7,product,quantity:1,unitPrice:product.price,subtotal:product.price,available:true,stock:8}],total:product.price,itemCount:1};
    const calls=[];const fetchMock=vi.fn(async(url,options={})=>{const body=options.body?JSON.parse(options.body):undefined;calls.push({url,method:options.method||'GET',body});if(url==='/api/users/me')return json(user);if(url==='/api/cart'&&options.method==='DELETE'){cart={items:[],total:0,itemCount:0};return new Response(null,{status:204});}if(url==='/api/cart'&&options.method==='GET')return json(cart);if(url==='/api/cart/items/7'&&options.method==='PUT'){cart={items:[{...cart.items[0],quantity:body.quantity,subtotal:product.price*body.quantity}],total:product.price*body.quantity,itemCount:body.quantity};return json(cart);}if(url==='/api/cart/items/7'&&options.method==='DELETE'){cart={items:[],total:0,itemCount:0};return new Response(null,{status:204});}return json([]);});
    vi.stubGlobal('fetch',fetchMock);startAt('/cart');const u=userEvent.setup();render(<App/>);
    await u.selectOptions(await screen.findByLabelText(/quantity for studio headphones/i),'2');
    await waitFor(()=>expect(calls.some(c=>c.url==='/api/cart/items/7'&&c.method==='PUT'&&c.body.quantity===2)).toBe(true));
    await u.click(screen.getByRole('button',{name:/remove/i}));
    expect(await screen.findByRole('heading',{name:/your bag is taking a breather/i})).toBeInTheDocument();
    expect(calls.some(c=>c.url==='/api/cart/items/7'&&c.method==='DELETE')).toBe(true);
  });

  it('loads order history and opens an order detail', async () => {
    const order={id:31,userId:4,productId:7,quantity:1,unitPrice:149.5,totalPrice:149.5,status:'PLACED',createdAt:'2026-11-26T10:00:00Z',items:[{productId:7,productName:product.name,unitPrice:149.5,quantity:1,subtotal:149.5}]};
    localStorage.setItem('shopsphere.jwt','jwt-user');const calls=[];const fetchMock=vi.fn(async(url,options={})=>{calls.push({url,method:options.method||'GET'});if(url==='/api/users/me')return json(user);if(url.startsWith('/api/orders?'))return json({content:[order],number:0,totalPages:1});if(url==='/api/orders/31')return json(order);return json([]);});
    vi.stubGlobal('fetch',fetchMock);startAt('/orders');const u=userEvent.setup();render(<App/>);
    await u.click(await screen.findByRole('link',{name:/studio headphones/i}));
    expect(await screen.findByRole('heading',{name:/order details/i})).toBeInTheDocument();
    expect(calls.some(c=>c.url==='/api/orders/31')).toBe(true);
  });

  it('protects customer pages and shows role-specific admin navigation', async () => {
    startAt('/orders'); renderApp('/orders');
    expect(await screen.findByRole('heading',{name:/sign in/i})).toBeInTheDocument();
    cleanup(); localStorage.clear();
    const api = fakeFetch(); localStorage.setItem('shopsphere.jwt','admin-token');
    api.fetchMock.mockImplementation(async (url,options={})=>{
      if(url==='/api/users/me')return json(admin);
      if(url==='/api/admin/dashboard')return json({totalOrders:12,pendingOrders:3,deliveredOrders:8,cancelledOrders:1,revenue:920.5,recentOrders:[]});
      return json([]);
    });
    vi.stubGlobal('fetch',api.fetchMock);startAt('/admin');render(<App/>);
    expect(await screen.findByText('Customers')).toBeInTheDocument();
    expect(screen.getByText('Products')).toBeInTheDocument();
    expect(await screen.findByText('12')).toBeInTheDocument();
  });
});

describe('ShopSphere administration', () => {
  it('loads customer records from user-service without fabricating customer data', async () => {
    localStorage.setItem('shopsphere.jwt','admin-token');
    const api=fakeFetch();api.fetchMock.mockImplementation(async(url,options={})=>{
      api.calls.push({url,method:options.method||'GET',body:options.body?JSON.parse(options.body):undefined});
      if(url==='/api/users/me')return json(admin);
      if(url.startsWith('/api/users'))return json([{...user,createdAt:'2026-11-20T12:00:00Z'}]);
      return json([]);
    });vi.stubGlobal('fetch',api.fetchMock);startAt('/admin/users');render(<App/>);
    expect(await screen.findByText('mira@example.com')).toBeInTheDocument();
    expect(screen.getByText('USER')).toBeInTheDocument();
    expect(api.calls.some(c=>c.url==='/api/users?q='||c.url==='/api/users')).toBe(true);
  });

  it('loads product availability controls from product-service', async () => {
    localStorage.setItem('shopsphere.jwt','admin-token');const api=fakeFetch();api.fetchMock.mockImplementation(async(url,options={})=>{
      api.calls.push({url,method:options.method||'GET',body:options.body?JSON.parse(options.body):undefined});
      if(url==='/api/users/me')return json(admin);
      if(url.startsWith('/api/products/admin/low-stock'))return json([]);
      if(url.startsWith('/api/products'))return json([product]);
      return json([]);
    });vi.stubGlobal('fetch',api.fetchMock);startAt('/admin/products');const u=userEvent.setup();render(<App/>);
    expect(await screen.findByText('Studio headphones')).toBeInTheDocument();
    await u.click(screen.getByRole('button',{name:/available/i}));
    await waitFor(()=>expect(api.calls.some(c=>c.url==='/api/products/7/availability'&&c.method==='PATCH'&&c.body.available===false)).toBe(true));
  });

  it('lists inactive products and reactivates them through the admin contract', async () => {
    localStorage.setItem('shopsphere.jwt','admin-token');
    const inactive={...product,id:19,name:'Archived headphones',active:false,available:false};
    let archived=[inactive];const api=fakeFetch();
    api.fetchMock.mockImplementation(async(url,options={})=>{
      api.calls.push({url,method:options.method||'GET',body:options.body?JSON.parse(options.body):undefined});
      if(url==='/api/users/me')return json(admin);
      if(url==='/api/products/admin/low-stock?threshold=5')return json([]);
      if(url==='/api/products/admin/inactive')return json(archived);
      if(url==='/api/products/19/reactivate'&&options.method==='PATCH'){archived=[];return json({...inactive,active:true,available:true});}
      if(url==='/api/products')return json([product]);
      return json([]);
    });
    vi.stubGlobal('fetch',api.fetchMock);startAt('/admin/products');const u=userEvent.setup();render(<App/>);
    await u.click(await screen.findByRole('button',{name:/inactive products/i}));
    expect(await screen.findByText('Archived headphones')).toBeInTheDocument();
    await u.click(screen.getByRole('button',{name:/reactivate/i}));
    await waitFor(()=>expect(api.calls.some(c=>c.url==='/api/products/19/reactivate'&&c.method==='PATCH')).toBe(true));
    expect(await screen.findByText(/no inactive products/i)).toBeInTheDocument();
  });

  it('creates products with the product-service request shape', async () => {
    localStorage.setItem('shopsphere.jwt','admin-token');const api=fakeFetch();api.fetchMock.mockImplementation(async(url,options={})=>{const body=options.body?JSON.parse(options.body):undefined;api.calls.push({url,method:options.method||'GET',body});if(url==='/api/users/me')return json(admin);if(url==='/api/products'&&options.method==='POST')return json({...product,...body},201);if(url.startsWith('/api/products'))return json([product]);return json([]);});
    vi.stubGlobal('fetch',api.fetchMock);startAt('/admin/products/new');const u=userEvent.setup();render(<App/>);
    await u.type(await screen.findByLabelText('Product name'),'Studio headphones');await u.type(screen.getByLabelText('Description'),'Warm, clear sound.');await u.type(screen.getByLabelText('Price'),'149.50');await u.type(screen.getByLabelText('Category'),'Audio');await u.type(screen.getByLabelText('Stock quantity'),'8');
    await u.click(screen.getByRole('button',{name:/create product/i}));
    await waitFor(()=>expect(api.calls.some(c=>c.url==='/api/products'&&c.method==='POST'&&c.body.name==='Studio headphones'&&c.body.category==='Audio'&&c.body.price===149.5&&c.body.stock===8)).toBe(true));
  });

  it('blocks a customer through the admin user-management contract', async () => {
    localStorage.setItem('shopsphere.jwt','admin-token');const api=fakeFetch();api.fetchMock.mockImplementation(async(url,options={})=>{
      const body=options.body?JSON.parse(options.body):undefined;api.calls.push({url,method:options.method||'GET',body});
      if(url==='/api/users/me')return json(admin);
      if(url==='/api/users/4'&&options.method==='GET')return json({...user,createdAt:'2026-11-20T12:00:00Z'});
      if(url==='/api/users/4/status')return json({...user,blocked:body.blocked});
      return json([]);
    });vi.stubGlobal('fetch',api.fetchMock);startAt('/admin/users/4');const u=userEvent.setup();render(<App/>);
    await u.click(await screen.findByRole('button',{name:/block account/i}));
    expect(await screen.findByText(/access is paused/i)).toBeInTheDocument();
    expect(api.calls.some(c=>c.url==='/api/users/4/status'&&c.method==='PATCH'&&c.body.blocked===true)).toBe(true);
  });

  it('updates an order status through the admin order-management contract', async () => {
    const order={id:31,userId:4,productId:7,quantity:1,unitPrice:149.5,totalPrice:149.5,status:'PLACED',createdAt:'2026-11-26T10:00:00Z',customer:{id:4,name:'Mira Sen',email:'mira@example.com',active:true},items:[{productId:7,productName:product.name,unitPrice:149.5,quantity:1,subtotal:149.5}]};let currentOrder=order;
    localStorage.setItem('shopsphere.jwt','admin-token');const api=fakeFetch();api.fetchMock.mockImplementation(async(url,options={})=>{
      const body=options.body?JSON.parse(options.body):undefined;api.calls.push({url,method:options.method||'GET',body});
      if(url==='/api/users/me')return json(admin);
      if(url.startsWith('/api/orders?'))return json({content:[order],number:0,totalPages:1});
      if(url==='/api/orders/31'&&options.method==='GET')return json(currentOrder);
      if(url==='/api/orders/31/status'){currentOrder={...currentOrder,status:body.status};return json(currentOrder);}
      return json([]);
    });vi.stubGlobal('fetch',api.fetchMock);startAt('/admin/orders');const u=userEvent.setup();render(<App/>);
    await u.click(await screen.findByRole('link',{name:/details/i}));
    expect(await screen.findByText('mira@example.com')).toBeInTheDocument();
    await u.click(await screen.findByRole('button',{name:/mark confirmed/i}));
    expect(await screen.findAllByText('CONFIRMED')).toHaveLength(2);
    expect(api.calls.some(c=>c.url==='/api/orders/31/status'&&c.method==='PATCH'&&c.body.status==='CONFIRMED')).toBe(true);
  });
});
