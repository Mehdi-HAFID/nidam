import Link from "@mui/material/Link";
import React from "react";

const Login = props => {

	const login = (event) => {
		event.preventDefault();

		const currentPath = "/";
		let url = new URL(process.env.REACT_APP_LOGIN_URL);

		url.searchParams.append(
			"post_login_success_uri",
			`${process.env.REACT_APP_BASE_URI}${currentPath}`
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