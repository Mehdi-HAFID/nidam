import React from "react";
import Link from "@mui/material/Link";
import {useLocation} from "react-router-dom";

import { CONFIG } from "../config";

const Login = props => {
	const location = useLocation();

	const login = (event) => {
		event.preventDefault();

		// const currentPath = "/";
		const currentPath = location.pathname + location.search + location.hash;
		console.log("currentPath: ", currentPath);
		let url = new URL(CONFIG.LOGIN_URL);

		url.searchParams.append(
			"post_login_success_uri",
			`${CONFIG.BASE_URI}${currentPath}`
		)

		window.location.href = url.toString();
	}

	return <Link onClick={e => login(e)}  style={{cursor: "pointer"}}>
		Already have an account? Sign in
	</Link>
	// <Button onClick={e => login(e)} variant="text" >
	// 	Already have an account? Sign in
	// </Button>
}

export default Login;