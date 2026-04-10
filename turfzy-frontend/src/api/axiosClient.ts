import axios from 'axios';

/**
* Central Axios instance for ALL API calls.
* - Base URL from env variable — swap between dev/prod without code changes
* - Request interceptor attaches JWT token automatically
* - Response interceptor handles 401 (token expired) globally
*/
const axiosClient = axios.create({
baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
timeout: 10000, // 10s timeout
headers: {
'Content-Type': 'application/json',
},
});

// Request interceptor — attach JWT token to every request
axiosClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('turfzy_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor — handle token expiry globally
axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Token expired — clear storage and redirect to login
      localStorage.removeItem('turfzy_token');
      localStorage.removeItem('turfzy_user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default axiosClient;