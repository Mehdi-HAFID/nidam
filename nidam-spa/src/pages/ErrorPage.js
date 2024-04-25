import React from "react";
import {Link as RouterLink} from "react-router-dom";
import CssBaseline from "@mui/material/CssBaseline";
import Typography from "@mui/material/Typography";
import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import {Paper} from "@mui/material";

const ErrorPage = () => {
	return (
        <div style={{
            backgroundImage: "linear-gradient( 109.6deg,  rgba(254,253,205,1) 11.2%, rgba(163,230,255,1) 91.1% )",
            height: "100vh", textAlign: "center", paddingTop: "40px"
        }}>
            <CssBaseline />

	        <Grid container spacing={0} direction="column" alignItems="center" justifyContent="center" sx={{height: '90%'}}>
	            <Grid item>
		            <Paper sx={{width: "650px", maxWidth: "80vw", paddingX: "2rem", paddingY: "4rem"}}
		                   elevation={0} square={false}>

			            <Typography variant="h3" sx={{pb: "10px"}}>Oops!</Typography>
			            <Typography variant="subtitle2" color="slategray" sx={{pb: "20px"}}>We can't find that page.</Typography>

				        <img src="../404-error.png" style={{width: "300px", maxWidth: "100vh"}}
				             className="mw-100 mh-300px theme-light-show" alt=""/>

			            <div>
				            <Button component={RouterLink} to="/" style={{textTransform: "none", backgroundColor: "#1B84FF"}} variant="contained">
		                        <Typography variant="h6">Return Home</Typography>
		                    </Button>
			            </div>
		            </Paper>
	            </Grid>
            </Grid>
        </div>);
};
export default ErrorPage;