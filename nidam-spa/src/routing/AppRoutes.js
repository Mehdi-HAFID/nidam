import React from "react";
import {useSelector} from "react-redux";
import {Navigate, Route, Routes} from "react-router-dom";

import SignUp from "../container/SignUp";
import Private from "../container/Private";
import ErrorPage from "../pages/ErrorPage";

const AppRoutes = () => {
	const authenticated = useSelector((state) => state.authentication.authenticated);

	console.log("authenticated: ", authenticated)
	return (<Routes>
		<Route path='error/404' element={<ErrorPage />} />
		{authenticated ? (
			<>
				<Route path="secret" element={<Private/>} />
				{/*<Route path='/*' element={<PrivateRoutes />} />*/}
				<Route index element={<Navigate to='/secret' />} />
			</>
		) : (
			<>
				<Route path="signup" element={<SignUp/>} />
				{/*<Route path='auth/*' element={<AuthPage />} />*/}
				<Route index element={<Navigate to='/signup' />} />
				{/*<Route path='*' element={<Navigate to='/auth' />} />*/}
			</>
		)}


		<Route path='*' element={<Navigate to='/error/404' />} />


		{/*<Route path='/' element={<Navigate to='signup' />} />*/}
		{/*<Route path="*" element={<ErrorPage />} />*/}
	</Routes>);

}

export default AppRoutes;