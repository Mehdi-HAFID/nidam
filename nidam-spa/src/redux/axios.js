import axios from "axios";

const instance = axios.create({
	baseURL: process.env.REACT_APP_BACKEND_REGISTRATION_URL,
	timeout: 15000,
});
// instance.defaults.headers.common['Authorization'] = localStorage.getItem('jwt');

export default instance;