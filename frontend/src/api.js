export class ApiError extends Error {
  constructor(status, message = 'Request failed') { super(message); this.status = status; }
}

let tokenReader = () => localStorage.getItem('shopsphere.jwt');
let onUnauthorized = () => {};
export function configureApi({ getToken, unauthorized } = {}) {
  if (getToken) tokenReader = getToken;
  if (unauthorized) onUnauthorized = unauthorized;
}

async function request(path, { method = 'GET', body, headers = {}, auth = true } = {}) {
  const requestHeaders = { Accept: 'application/json', ...headers };
  if (body !== undefined) requestHeaders['Content-Type'] = 'application/json';
  const token = tokenReader();
  if (auth && token) requestHeaders.Authorization = `Bearer ${token}`;
  let response;
  try { response = await fetch(path, { method, headers: requestHeaders, ...(body !== undefined ? { body: JSON.stringify(body) } : {}) }); }
  catch { throw new ApiError(0, 'Could not reach ShopSphere services. Check your connection and retry.'); }
  if (response.status === 401 && auth) onUnauthorized();
  if (!response.ok) {
    let message = response.statusText || 'Request failed';
    try { const data = await response.json(); message = data.error || data.message || message; } catch { /* response has no JSON body */ }
    throw new ApiError(response.status, message);
  }
  if (response.status === 204) return null;
  return response.json();
}
const get = (url, auth = true) => request(url, { auth });
const post = (url, body, headers = {}) => request(url, { method: 'POST', body, headers });
const put = (url, body) => request(url, { method: 'PUT', body });
const patch = (url, body) => request(url, { method: 'PATCH', body });
const del = (url) => request(url, { method: 'DELETE' });
const query = (params) => { const q = new URLSearchParams(); Object.entries(params).forEach(([k,v]) => { if (v !== undefined && v !== null && v !== '') q.set(k, v); }); return q.size ? `?${q}` : ''; };

export const register = (data) => request('/api/auth/register', { method: 'POST', body: data, auth: false });
export const login = (data) => request('/api/auth/login', { method: 'POST', body: data, auth: false });
export const logout = () => request('/api/auth/logout', { method: 'POST' });
export const getProfile = () => get('/api/users/me');
export const updateProfile = (data) => put('/api/users/me', data);
export const changePassword = (data) => put('/api/users/me/password', data);
export const listUsers = (q = '') => get(`/api/users${query({ q })}`);
export const getUser = (id) => get(`/api/users/${id}`);
export const updateUser = (id, data) => put(`/api/users/${id}`, data);
export const setUserBlocked = (id, blocked) => patch(`/api/users/${id}/status`, { blocked });
export const deleteUser = (id) => del(`/api/users/${id}`);

export const listProducts = (params = {}) => get(`/api/products${query(params)}`, false);
export const listProductsPage = (params = {}) => get(`/api/products/page${query(params)}`, false);
export const getProduct = (id) => get(`/api/products/${id}`, false);
export const createProduct = (data) => post('/api/products', data);
export const updateProduct = (id, data) => put(`/api/products/${id}`, data);
export const deactivateProduct = (id) => del(`/api/products/${id}`);
export const updateStock = (id, quantity) => patch(`/api/products/${id}/stock`, { quantity });
export const setAvailability = (id, available) => patch(`/api/products/${id}/availability`, { available });
export const lowStockProducts = (threshold = 5) => get(`/api/products/admin/low-stock${query({ threshold })}`);
export const listInactiveProducts = () => get('/api/products/admin/inactive');
export const reactivateProduct = (id) => patch(`/api/products/${id}/reactivate`);

const cartChanged = () => { if (typeof window !== 'undefined') window.dispatchEvent(new Event('shopsphere:cart-updated')); };
export const getCart = () => get('/api/cart');
export const addCartItem = async (productId, quantity) => { const result = await post('/api/cart/items', { productId, quantity }); cartChanged(); return result; };
export const setCartQuantity = async (productId, quantity) => { const result = await put(`/api/cart/items/${productId}`, { quantity }); cartChanged(); return result; };
export const removeCartItem = async (id) => { const result = await del(`/api/cart/items/${id}`); cartChanged(); return result; };
export const clearCart = async () => { const result = await del('/api/cart'); cartChanged(); return result; };
export const checkout = async (key) => { const result = await post('/api/orders/checkout', undefined, { 'Idempotency-Key': key }); cartChanged(); return result; };
export const listOrders = (params = {}) => get(`/api/orders${query(params)}`);
export const getOrder = (id) => get(`/api/orders/${id}`);
export const cancelOrder = (id) => request(`/api/orders/${id}/cancel`, { method: 'PATCH' });
export const updateOrderStatus = (id, status) => patch(`/api/orders/${id}/status`, { status });
export const getDashboard = () => get('/api/admin/dashboard');
