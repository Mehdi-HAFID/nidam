import { createSlice } from '@reduxjs/toolkit';

const initialState = {
    secretLoading : false,
    secretError : null,
    secret: null
}

export const demoSlice = createSlice({
    name: 'demo',
    initialState,
    reducers: {
        getSecretStart: (state, action) => {
            state.secretLoading = true;
            state.secretError = null;
            state.secret = null;
        },
        getSecretSuccess: (state, action) => {
            state.secretLoading = false;
            state.secretError = null;
            state.secret = action.payload.secret;
        },
        getSecretFail: (state, action) => {
            state.secretLoading = false;
            state.secretError = action.payload.error;
        },
        getSecretResetError: (state) => {
            state.secretError = null;
        },
    },
})

// Action creators are generated for each case reducer function
export const {getSecretStart, getSecretSuccess, getSecretFail,
    getSecretResetError } = demoSlice.actions

export default demoSlice.reducer;