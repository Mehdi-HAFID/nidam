import { createSlice } from '@reduxjs/toolkit';

const initialState = {
    secretLoading : false,
    secretError : null,
    secret: null,

    topSecretLoading : false,
    topSecretError : null,
    topSecret: null,
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

        getTopSecretStart: (state, action) => {
            state.topSecretLoading = true;
            state.topSecretError = null;
            state.topSecret = null;
        },
        getTopSecretSuccess: (state, action) => {
            state.topSecretLoading = false;
            state.topSecretError = null;
            state.topSecret = action.payload.topSecret;
        },
        getTopSecretFail: (state, action) => {
            state.topSecretLoading = false;
            state.topSecretError = action.payload.error;
        },
        getTopSecretResetError: (state) => {
            state.topSecretError = null;
        }
    },
})

// Action creators are generated for each case reducer function
export const {getSecretStart, getSecretSuccess,
    getSecretFail, getSecretResetError ,
    getTopSecretStart, getTopSecretSuccess,
    getTopSecretFail, getTopSecretResetError} = demoSlice.actions

export default demoSlice.reducer;