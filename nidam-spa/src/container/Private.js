import React, {useEffect} from "react";
import {useDispatch, useSelector} from "react-redux";
import {Stack, Box, Button, Typography, Container, CssBaseline} from "@mui/material";

import * as nidamActions from "../redux/nidam";
import Logout from "./Logout";


const Private = props => {
	const secret = useSelector((state) => state.nidam.secret);
	const userInfo = useSelector((state) => state.authentication.userInfo);
	const dispatch = useDispatch();

	useEffect(() => {
		dispatch(nidamActions.GetSecret());
	}, []);

    const getSecret = () => {
        dispatch(nidamActions.GetSecret());
    }

    // const expired = () => {
    //     console.log("userInfo exp: ", (userInfo.exp * 1000));
    //     console.log("userInfo ---: ", new Date().getTime());
    //     const expireAt = ((userInfo.exp * 1000) - (new Date().getTime())) - 30_000;
    //     console.log("difference: ", ((userInfo.exp * 1000) - (new Date().getTime())) - 30_000); // 30 seconds before token expires
    //
    //     dispatch(authenticationActions.logoutBeforeTokenExpires(expireAt));
    // }

	return (
		<div style={{
			// backgroundColor: "#08AEEA",
			// backgroundImage: "linear-gradient(0deg, #08AEEA 0%, #ffffff 100%)",
			backgroundImage: "linear-gradient( 109.6deg,  rgba(254,253,205,1) 11.2%, rgba(163,230,255,1) 91.1% )",
			height: "100vh", textAlign: "center", paddingTop: "40px"
		}}>
			<CssBaseline />
			<Container component="main" maxWidth="xl" >
                <Stack spacing={2} alignItems="center">
					<Typography variant="h5" >Page for Authenticated Users</Typography>
					<Logout/>
					<Typography variant="h2" >You're In</Typography>
                    <Button variant="contained" size="large" onClick={getSecret}>Call Resource Server</Button>
                    {/*<Button variant="contained" sx={{mr: "20px"}} size="large" onClick={expired}>Expired Token</Button>*/}
                    <Box
                        sx={{
                            maxHeight: 200,          // Limit height
                            overflowY: 'auto',       // Vertical scroll
                            p: 2,                    // Padding
                            border: '1px solid #ccc',
                            borderRadius: 2,
                            width: '100%',
                        }}
                    >
                            {JSON.stringify(secret)}
                    </Box>
                </Stack>
			</Container>
		</div>
	);
}

export default Private;