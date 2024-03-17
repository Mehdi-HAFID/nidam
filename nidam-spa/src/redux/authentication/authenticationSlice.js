import { createSlice } from '@reduxjs/toolkit';

const initialState = {
	isLoggedInLoading : false,
	isLoggedInError : null,
	userInfo : null,
	authenticated: false
}

export const authenticationSlice = createSlice({
	name: 'authentication',
	initialState,
	reducers: {
		isLoggedInStart: (state, action) => {
			state.isLoggedInLoading = true;
			state.isLoggedInError = null;
		},
		isLoggedInSuccess: (state, action) => {
			state.isLoggedInLoading = false;
			state.isLoggedInError = null;
			state.userInfo = action.payload.userInfo;
		},
		isLoggedInFail: (state, action) => {
			state.isLoggedInLoading = false;
			state.isLoggedInError = action.payload.error;
			state.userInfo = null;
		},
		isLoggedInResetError: (state) => {
			state.isLoggedInError = null;
		},
		authenticated: (state) => {
			state.authenticated = true;
		}
	},
})

// Action creators are generated for each case reducer function
export const { isLoggedInStart, isLoggedInSuccess,
	isLoggedInFail, isLoggedInResetError , authenticated} = authenticationSlice.actions

export default authenticationSlice.reducer