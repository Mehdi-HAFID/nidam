import React, {useEffect} from "react";
import {useDispatch, useSelector} from "react-redux";
import CssBaseline from "@mui/material/CssBaseline";
import Container from "@mui/material/Container";

import * as registerSagas from "../redux/register/saga";
import Logout from "./Logout";
import {Global, css } from "@emotion/react";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import Link from "@mui/material/Link";

const Private = props => {
	// const secret = useSelector((state) => state.register.secret);
	const dispatch = useDispatch();

	// useEffect(() => {
	// 	dispatch(registerSagas.GetMySecret());
	// }, []);


	// console.log("secret", secret);
	return (
		<div style={{
			// backgroundColor: "#08AEEA",
			// backgroundImage: "linear-gradient(0deg, #08AEEA 0%, #ffffff 100%)",
			backgroundImage: "linear-gradient( 109.6deg,  rgba(254,253,205,1) 11.2%, rgba(163,230,255,1) 91.1% )",
			height: "100vh", textAlign: "center", paddingTop: "40px"
		}}>
			{/*<Global*/}
			{/*	styles={css` body { margin: 0; } `}*/}
			{/*/>*/}
			<CssBaseline />
			<Container component="main" maxWidth="xl" >
				<div >
					<Typography variant="h5" sx={{mb: "10px"}}>Page for Authenticated Users</Typography>
					<Logout/>
					<Typography variant="h2" sx={{mt: "20px"}}>
						Checkout The Treasure
					</Typography>
					<Link href="https://tigmat.io" underline="none" variant="h1">Tigmat</Link>
				</div>
			</Container>
			{/*<Grid*/}
			{/*	container*/}
			{/*	spacing={0}*/}
			{/*	direction="column"*/}
			{/*	alignItems="center"*/}
			{/*	justifyContent="center"*/}
			{/*	sx={{ minHeight: '100vh' }}*/}
			{/*>*/}
			{/*	<Grid item xs={3}>*/}
			{/*		<h4 className="">Page for Authenticated Users</h4>*/}
			{/*		<Logout/>*/}
			{/*	</Grid>*/}
			{/*</Grid>*/}
		</div>
	);
}

export default Private;