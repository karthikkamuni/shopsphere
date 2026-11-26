// Thin wrapper around fetch. All URLs are relative (/api/...): nginx (production) or the
// Vite dev proxy (local development) forwards each prefix to the right service.

export class ApiError extends Error {
  constructor(status) {
    super(`Request failed with status ${status}`);
    this.status = status; // 0 means the network request itself failed
  }
}

async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(path, options);
  } catch {
    throw new ApiError(0);
  }
  if (!response.ok) {
    throw new ApiError(response.status);
  }
  return response.json();
}

function post(path, body) {
  return request(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(body),
  });
}

const get = (path) => request(path, { headers: { Accept: 'application/json' } });

export const listUsers = () => get('/api/users');
export const createUser = (user) => post('/api/users', user);

export const listProducts = () => get('/api/products');
export const createProduct = (product) => post('/api/products', product);

export const listOrders = () => get('/api/orders');
export const createOrder = (order) => post('/api/orders', order);
