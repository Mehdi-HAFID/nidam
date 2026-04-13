import axios from "axios";

// const registerAxios = axios.create({
// 	baseURL: process.env.REACT_APP_BACKEND_REGISTRATION_URL,
// 	timeout: 15000,
// });
//
// const resourceServerAxios = axios.create({
// 	baseURL: process.env.REACT_APP_RESOURCE_SERVER_URI,
// 	timeout: 15000,
// });

import { CONFIG } from "../config";

const registerAxios = axios.create({
	baseURL: CONFIG.BACKEND_REGISTRATION_URL,
	timeout: 15000,
});

const resourceServerAxios = axios.create({
	baseURL: CONFIG.RESOURCE_SERVER_URI,
	timeout: 15000,
});

export {registerAxios, resourceServerAxios as axios};