import { createSlice } from '@reduxjs/toolkit';

const initialState = {
	registrationLoading : false,
	registrationError : null,
	registeredUser : null
}

export const registerSlice = createSlice({
	name: 'register',
	initialState,
	reducers: {
		registerStart: (state, action) => {
			state.registrationLoading = true;
			state.registrationError = null
		},
		registerSuccess: (state, action) => {
			console.log("action: ", action);
			state.registrationLoading = false;
			state.registrationError = null;
			state.registeredUser = action.payload.user;
		},
		registerFail: (state, action) => {
			state.registrationLoading = false;
			state.registrationError = action.payload.error;
			state.registeredUser = null;
		},
		registerResetError: (state) => {
			state.registrationError = null;
		}
	},
})

// Action creators are generated for each case reducer function
export const { registerStart, registerSuccess,
	registerFail, registerResetError } = registerSlice.actions

export default registerSlice.reducer