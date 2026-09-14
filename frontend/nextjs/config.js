// const runtimeConfig = window.NIDAM_CONFIG || {};
const runtimeConfig = typeof window !== 'undefined' ? window.NIDAM_CONFIG || {} : {};

export const CONFIG = {
	BACKEND_REGISTRATION_URL:
		runtimeConfig.BACKEND_REGISTRATION_URL ||
		process.env.NEXT_PUBLIC_BACKEND_REGISTRATION_URL,

	BASE_URI:
		runtimeConfig.BASE_URI ||
		process.env.NEXT_PUBLIC_BASE_URI,

	RESOURCE_SERVER_URI:
		runtimeConfig.RESOURCE_SERVER_URI ||
		process.env.NEXT_PUBLIC_RESOURCE_SERVER_URI,

	LOGIN_URL:
		runtimeConfig.LOGIN_URL ||
		process.env.NEXT_PUBLIC_LOGIN_URL,

	LOGOUT_URL:
		runtimeConfig.LOGOUT_URL ||
		process.env.NEXT_PUBLIC_LOGOUT_URL,

	PROFILE_PUBLIC_ENDPOINT:
		runtimeConfig.PROFILE_PUBLIC_ENDPOINT ||
		process.env.NEXT_PUBLIC_PROFILE_PUBLIC_ENDPOINT,

	BASE_PATH:
		runtimeConfig.BASE_PATH ||
		process.env.NEXT_PUBLIC_BASE_PATH,
};

