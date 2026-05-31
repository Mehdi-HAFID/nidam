import React, {useEffect} from "react";
import {useDispatch, useSelector} from "react-redux";
import {Stack, Box, Button, Typography, Container, CssBaseline, Divider, Alert, Card, CardContent} from "@mui/material";
import LockOpenIcon from "@mui/icons-material/LockOpen";
import LockIcon from "@mui/icons-material/Lock"

import * as nidamSaga from "../redux/nidam";
import Logout from "./Logout";
import {getTopSecretResetError} from "../redux/nidam/demoSlice"
import Grid from "@mui/material/Grid";
import Link from "@mui/material/Link";


const Private = props => {
	const secret = useSelector((state) => state.nidam.secret);
	const secretError = useSelector((state) => state.nidam.secretError);
	const userInfo = useSelector((state) => state.authentication.userInfo);

	const topSecretError = useSelector((state) => state.nidam.topSecretError);

	const dispatch = useDispatch();

	useEffect(() => {
		getSecret();
		getTopSecret();
	}, []);

    const getSecret = () => {
        dispatch(nidamSaga.GetSecret());
    }

	const getTopSecret = () => {
		dispatch(nidamSaga.GetTopSecret());
	}

	const clearTopSecretError = () => {
		dispatch(getTopSecretResetError());
	}

    // const expired = () => {
    //     console.log("userInfo exp: ", (userInfo.exp * 1000));
    //     console.log("userInfo ---: ", new Date().getTime());
    //     const expireAt = ((userInfo.exp * 1000) - (new Date().getTime())) - 30_000;
    //     console.log("difference: ", ((userInfo.exp * 1000) - (new Date().getTime())) - 30_000); // 30 seconds before token expires
    //
    //     dispatch(authenticationActions.logoutBeforeTokenExpires(expireAt));
    // }



	// return (
	// 	<div style={{
	// 		// backgroundColor: "#08AEEA",
	// 		// backgroundImage: "linear-gradient(0deg, #08AEEA 0%, #ffffff 100%)",
	// 		backgroundImage: "linear-gradient( 109.6deg,  rgba(254,253,205,1) 11.2%, rgba(163,230,255,1) 91.1% )",
	// 		height: "100vh", textAlign: "center", paddingTop: "40px"
	// 	}}>
	// 		<CssBaseline />
	// 		<Container component="main" maxWidth="xl" >
    //             <Stack spacing={2} alignItems="center">
	// 				<Typography variant="h5" >Page for Authenticated Users</Typography>
	// 				<Logout/>
	// 				<Typography variant="h2" >You're In</Typography>
	//
	// 				{/*<Divider color="#ccc" sx={{width: "100%", mb: "20px"}}/>*/}
	//
	// 				<Grid container spacing={2}>
	// 					<Grid item md={6}>
	//
	// 						<Divider sx={{width: "100%"}}><Typography variant="h5" >"/demo" endpoint: allowed</Typography></Divider>
	//
	// 						<Button variant="contained" size="large" onClick={getSecret}>Call /demo</Button>
	// 						{/*<Button variant="contained" sx={{mr: "20px"}} size="large" onClick={expired}>Expired Token</Button>*/}
	// 						{secret && <Box
	// 							sx={{
	// 								maxHeight: 200,          // Limit height
	// 								overflowY: 'auto',       // Vertical scroll
	// 								p: 2,                    // Padding
	// 								border: '1px solid #ccc',
	// 								borderRadius: 2,
	// 								width: '100%',
	// 							}}
	// 						>
	// 							{JSON.stringify(secret)}
	// 						</Box>}
	// 						{secretError && <Typography variant="h6" color="error">{secretError}</Typography>}
	// 					</Grid>
	//
	// 					<Grid item md={6}>
	// 						<Divider sx={{width: "100%"}}><Typography variant="h5" >"/top-secret" endpoint: not allowed</Typography></Divider>
	//
	// 						<Button variant="contained" size="large" onClick={getTopSecret}>Call /top-secret</Button>
	//
	// 						{ topSecretError &&
	// 							<Alert severity="error" onClose={clearTopSecretError}>{topSecretError}</Alert>
	// 						}
	// 					</Grid>
	// 				</Grid>
	//
	//
	//
	//
	//
	//
    //             </Stack>
	// 		</Container>
	// 	</div>
	// );

	return (
		<Box
			sx={{
				minHeight: "100vh",
				background:
					"linear-gradient(109.6deg, rgba(254,253,205,1) 11.2%, rgba(163,230,255,1) 91.1%)",
				py: 5
			}}
		>
			<CssBaseline />

			<Container maxWidth="lg">
				<Stack spacing={3} alignItems="center" textAlign="center">

					<Typography variant="h5">
						Page for Authenticated Users
					</Typography>

					<Logout />

					<Typography variant="h3" fontWeight={600}>
						You're In
					</Typography>

					<Grid container spacing={3} sx={{ mt: 2 }}>
						<Grid item xs={12} md={6}>
							<Card elevation={4} sx={{
								borderTop: "4px solid",
								borderColor: "success.main",
								backgroundColor: "rgba(240, 255, 240, 0.7)"
							}}>
								<CardContent>
									<Stack spacing={2}>
										<Divider>
											<LockOpenIcon color="success" />
											<Typography variant="h6">
												"/demo" endpoint (allowed)
											</Typography>
										</Divider>

										<Button variant="contained" size="large" onClick={getSecret}>Call /demo</Button>

										{secret &&
											<Box
												sx={{
													maxHeight: 200,
													overflowY: "auto",
													p: 2,
													border: "1px solid #ccc",
													borderRadius: 2,
													fontFamily: "monospace",
													textAlign: "left"
												}}
											>
												{JSON.stringify(secret, null, 2)}
											</Box>
										}

										{secretError && <Alert severity="error">{secretError}</Alert>}
									</Stack>
								</CardContent>
							</Card>
						</Grid>

						<Grid item xs={12} md={6}>
							<Card elevation={4} sx={{
								borderTop: "4px solid orange",
								backgroundColor: "rgba(255,255,255,0.85)",
								backdropFilter: "blur(6px)",
							}}>
								<CardContent>
									<Stack spacing={2}>
										<Divider>
											<LockIcon color="error" />
											<Typography variant="h6">"/top-secret" endpoint (forbidden)</Typography>
										</Divider>

										<Button variant="contained" size="large" onClick={getTopSecret}>Call /top-secret</Button>

										{topSecretError && <Alert severity="error" onClose={clearTopSecretError}>
											{topSecretError}
										</Alert>
										}
									</Stack>
								</CardContent>
							</Card>
						</Grid>

					</Grid>
					{/*<Box*/}
					{/*	component="img"*/}
					{/*	src="android-chrome-512x512.png"*/}
					{/*	alt="Nidam Logo"*/}
					{/*	onClick={() => window.location.href = "https://nidam.derbyware.com"}*/}
					{/*	sx={{*/}
					{/*		width: 200,*/}
					{/*		height: 200,*/}
					{/*		objectFit: "contain",*/}
					{/*		mb: 1,*/}
					{/*		cursor: "pointer",*/}
					{/*	}}*/}
					{/*/>*/}
					<Link
						href="https://nidam.derbyware.com"
						target="_blank"
						rel="noopener noreferrer"
						underline="none"
					>
						<Box
							component="img"
							src="android-chrome-512x512.png"
							alt="Nidam Logo"
							sx={{
								width: 200,
								height: 200,
								objectFit: "contain",
								cursor: "pointer"
							}}
						/>
					</Link>
				</Stack>
			</Container>
		</Box>
	);
}

export default Private;