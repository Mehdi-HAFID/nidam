import React from 'react';
import ReactDOM from 'react-dom/client';
import {BrowserRouter, Navigate, Route, Routes} from "react-router-dom";
import {Provider} from "react-redux";

import reportWebVitals from './reportWebVitals';
import SignUp from "./container/SignUp";
import {store} from "./redux/store";
// import './index.css';
import ErrorPage from "./pages/ErrorPage";

import '@fontsource/roboto/300.css';
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/500.css';
import '@fontsource/roboto/700.css';
import Private from "./container/Private";
import AuthenticationStartup from "./authentication/AuthenticationStartup";
import AppRoutes from "./routing/AppRoutes";
import {css, Global} from "@emotion/react";

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
      <Provider store={store}>
          <BrowserRouter basename="/react-ui" >
              <AuthenticationStartup>
                  {/* emotion: this must be added to get rid of the top margin that won't go away when everything is set to margin: 0*/}
                  <Global
                      styles={css` body { margin: 0; } `}
                  />
                  <AppRoutes/>
              </AuthenticationStartup>
          </BrowserRouter>
      </Provider>
    {/*<App />*/}
  </React.StrictMode>
);

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
