const runtimeConfig = window.NIDAM_CONFIG || {};

// if(window.NIDAM_CONFIG){
// 	console.log("using config.js");
// } else {
// 	console.log("using .env");
// }

export const CONFIG = {
	BACKEND_REGISTRATION_URL:
		runtimeConfig.BACKEND_REGISTRATION_URL ||
		process.env.REACT_APP_BACKEND_REGISTRATION_URL,

	BASE_URI:
		runtimeConfig.BASE_URI ||
		process.env.REACT_APP_BASE_URI,

	RESOURCE_SERVER_URI:
		runtimeConfig.RESOURCE_SERVER_URI ||
		process.env.REACT_APP_RESOURCE_SERVER_URI,

	LOGIN_URL:
		runtimeConfig.LOGIN_URL ||
		process.env.REACT_APP_LOGIN_URL,

	LOGOUT_URL:
		runtimeConfig.LOGOUT_URL ||
		process.env.REACT_APP_LOGOUT_URL,
};


// only for reference
// window.NIDAM_CONFIG = {
//
// 	REACT_APP_BACKEND_REGISTRATION_URL: 'http://localhost:4000/',
// 	REACT_APP_BASE_URI: 'http://localhost:7080/react-ui',
// 	REACT_APP_RESOURCE_SERVER_URI: 'http://localhost:7080/bff/api',
// 	REACT_APP_LOGIN_URL: 'http://localhost:7080/bff/oauth2/authorization/token-generator'
// };
