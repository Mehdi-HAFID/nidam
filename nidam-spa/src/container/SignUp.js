import React, {useEffect, useState} from 'react';
import Avatar from '@mui/material/Avatar';
import Button from '@mui/material/Button';
import CssBaseline from '@mui/material/CssBaseline';
import TextField from '@mui/material/TextField';
import FormControlLabel from '@mui/material/FormControlLabel';
import Checkbox from '@mui/material/Checkbox';
import Link from '@mui/material/Link';
import Grid from '@mui/material/Grid';
import Box from '@mui/material/Box';
import AccountBoxIcon from '@mui/icons-material/AccountBox';
import Typography from '@mui/material/Typography';
import Container from '@mui/material/Container';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import {useDispatch, useSelector} from "react-redux";
import {Alert, FormControl, FormGroup, FormHelperText, IconButton, InputAdornment, InputLabel, OutlinedInput} from "@mui/material";

import {checkValidity} from "../other/utility";
import * as registerSagas from "../redux/register/saga";
import {registerResetError} from "../redux/register/registerSlice";
import Login from "./Login";
import {NavLink} from "react-router-dom";
import {isLoggedInResetError} from "../redux/authentication/authenticationSlice";

// import Link as LL from 'react'

function Copyright(props) {
	return (
		<Typography variant="body2" color="text.secondary" align="center" {...props}>
			{'Copyright © '}
			<Link color="inherit" href="https://tigmat.io/">
				Nidam By Mehdi Hafid
			</Link>{' '}
			{new Date().getFullYear()}
			{'.'}
		</Typography>
	);
}


const changeText = (inputTextState, value, label) => {
	const updatedInputTextState = {...inputTextState};
	updatedInputTextState.value = value;
	const [valid, message] = checkValidity(updatedInputTextState, label);
	updatedInputTextState.valid = valid;
	updatedInputTextState.validationMessage = message;
	return updatedInputTextState;
}

const SignUp = (props) => {
	const dispatch = useDispatch();

	const registrationLoading = useSelector((state) => state.register.registrationLoading)
	const registeredUser = useSelector((state) => state.register.registeredUser)
	const registrationError = useSelector((state) => state.register.registrationError)

	const isLoggedInError = useSelector((state) => state.authentication.isLoggedInError);

	useEffect(() => {
		document.title = "Sign up - Nidam By Mehdi Hafid";
		// ReCaptcha
		const script = document.createElement('script');
		script.src = 'https://www.google.com/recaptcha/api.js?render=6LcyyEMpAAAAAMztnW6xVq1HFD0b-mlyk2t6NZa-';
		script.class = "external-script"
		script.async = true;
		script.defer = true;
		document.body.appendChild(script);
		return () => {
			document.body.removeChild(script);
		};
	}, []);

	const executeReCaptcha = (e) => {
		e.preventDefault();
		window.grecaptcha.ready(function() {
			window.grecaptcha.execute('6LcyyEMpAAAAAMztnW6xVq1HFD0b-mlyk2t6NZa-', {action: 'submit'})
				// .then(token => verifyReCaptcha(token))
				.then(token => register(token))
		});
	}

	const register = token => {
		setTouchedPassword(true);

		setShowTermsError(!termsAccepted)

		if (!checkAllInputsValidity()) {
			return;
		}
		const user = {
			email: email.value,
			password: password.value,
			recaptchaKey: token
		}
		console.log("user: ", user);
		dispatch(registerSagas.register(user));
	}

	const [email, setEmail] = useState({
		value: "",
		validation: {
			required: true,
			maxLength: 320,
			isEmail: true
		},
		valid: true,
		validationMessage: null
	});

	const [password, setPassword] = useState({
		value: '',
		validation: {
			required: true,
			minLength: 4,
			maxLength: 255
		},
		valid: false,
		validationMessage: "Invalid Password"
	});

	const [confirmPassword, setConfirmPassword] = useState({
		value: '',
		validation: {
			required: true,
			minLength: 4,
			maxLength: 255
		},
		valid: false
	});

	const [touchedPassword, setTouchedPassword] = useState(false);

	const emailChangeHandler = (event) => {
		const updatedEmail = changeText(email, event.target.value, "Email");
		setEmail(updatedEmail);
	}

	const passwordChangeHandler = (event) => {
		setTouchedPassword(true);
		const updatedPassword = changeText(password, event.target.value, "Password");

		if (!updatedPassword.valid) {
			setPassword(updatedPassword);
		} else {
			if (updatedPassword.value !== confirmPassword.value) {
				updatedPassword.valid = false;
				updatedPassword.validationMessage = "Password and Confirmation don't match";
				setPassword(updatedPassword);
			} else {
				updatedPassword.valid = true;
				updatedPassword.validationMessage = null;
				setPassword(updatedPassword);

				const updatedConfirmPassword = {...confirmPassword};
				updatedConfirmPassword.valid = true;
				updatedConfirmPassword.validationMessage = null;
				setConfirmPassword(updatedConfirmPassword);
			}
		}

	}

	const confirmPasswordChangeHandler = (event) => {
		const updatedConfirmPassword = changeText(confirmPassword, event.target.value, "Password Confirmation");

		if (!updatedConfirmPassword.valid) {
			setConfirmPassword(updatedConfirmPassword);
		} else {
			if (updatedConfirmPassword.value !== password.value) {
				updatedConfirmPassword.valid = false;
				updatedConfirmPassword.validationMessage = "Password and Confirmation don't match";
				setConfirmPassword(updatedConfirmPassword);
			} else {
				updatedConfirmPassword.valid = true;
				updatedConfirmPassword.validationMessage = null;
				setConfirmPassword(updatedConfirmPassword);

				const updatedPassword = {...password};
				updatedPassword.valid = true;
				updatedPassword.validationMessage = null;
				setPassword(updatedPassword);
			}
		}
	}


	const checkAllInputsValidity = () => {
		let allValid = true;
		const updatedEmail = changeText(email, email.value, "Email");
		setEmail(updatedEmail);
		allValid = allValid && updatedEmail.valid;

		const updatedPassword = changeText(password, password.value, "Password");
		setPassword(updatedPassword);
		allValid = allValid && password.valid;

		const updatedConfirmPassword = changeText(confirmPassword, confirmPassword.value, "Password Confirmation");
		setConfirmPassword(updatedConfirmPassword);
		allValid = allValid && confirmPassword.valid;
		return allValid;
	}

	const [termsAccepted, setTermsAccepted] = useState(false);
	const [showTermsError, setShowTermsError] = useState(false);

	const [showPassword, setShowPassword] = useState(false);

	const handleClickShowPassword = () => setShowPassword((show) => !show);

	return (
		<Container component="main" maxWidth="xs">
			<CssBaseline/>
			<Box
				sx={{
					marginTop: 8,
					display: 'flex',
					flexDirection: 'column',
					alignItems: 'center',
				}}
			>
				<Avatar sx={{m: 1, bgcolor: 'info.main'}}>
					<AccountBoxIcon/>
				</Avatar>
				<Typography component="h1" variant="h5">
					Sign up
				</Typography>

				{
					(!registrationLoading && registeredUser !== null) ?
						<Alert severity="success">You're successfully registered.</Alert> : null
				}

				{
					( registrationError !== null) ?
						<Alert severity="error" onClose={() => dispatch(registerResetError())}>{registrationError}</Alert> : null
				}

				{/* /me error TODO add Try Again Button */}
				{
					( isLoggedInError !== null) ?
						<Alert severity="error" sx={{mt: 2}} onClose={() => dispatch(isLoggedInResetError())}>{isLoggedInError}</Alert> : null
				}

				<Box sx={{mt: 1}}>
					<TextField
						margin="normal"
						required
						fullWidth
						id="email"
						label="Email Address"
						name="email"
						autoComplete="email"
						autoFocus
						value={email.value}
						onChange={emailChangeHandler}
						helperText={!email.valid ? email.validationMessage : ""}
						error={!email.valid}
					/>
					<FormControl
						// sx={{ m: 1, width: '25ch' }}
						variant="outlined"
						fullWidth
						margin="normal"
						error={touchedPassword && !password.valid}
					>
						<InputLabel htmlFor="password" >Password</InputLabel>
						<OutlinedInput
							// id="outlined-adornment-password"
							endAdornment={
								<InputAdornment position="end">
									<IconButton
										aria-label="toggle password visibility"
										onClick={handleClickShowPassword}
										// onMouseDown={handleMouseDownPassword}
										edge="end"
									>
										{showPassword ? <VisibilityOff /> : <Visibility />}
									</IconButton>
								</InputAdornment>
							}
							// margin="normal"
							required
							name="password"
							label="Password"
							type={showPassword ? 'text' : 'password'}
							id="password"
							autoComplete="new-password"
							value={password.value}
							onChange={passwordChangeHandler}
						/>
						<FormHelperText>
							{(touchedPassword && !password.valid) ? password.validationMessage : ""}
						</FormHelperText>
					</FormControl>
					{/*<TextField*/}
					{/*	margin="normal"*/}
					{/*	required*/}
					{/*	fullWidth*/}
					{/*	name="password"*/}
					{/*	label="Password"*/}
					{/*	type="password"*/}
					{/*	id="password"*/}
					{/*	autoComplete="new-password"*/}
					{/*	value={password.value}*/}
					{/*	onChange={passwordChangeHandler}*/}
					{/*	helperText={(touchedPassword && !password.valid) ? password.validationMessage : ""}*/}
					{/*	error={touchedPassword && !password.valid}*/}
					{/*/>*/}
					<FormControl
						// sx={{ m: 1, width: '25ch' }}
						variant="outlined"
						fullWidth
						margin="normal"
						error={touchedPassword && !confirmPassword.valid}
					>
						<InputLabel htmlFor="password-confirmation" >Confirm Password</InputLabel>
						<OutlinedInput
							// id="outlined-adornment-password"
							endAdornment={
								<InputAdornment position="end">
									<IconButton
										aria-label="toggle password visibility"
										onClick={handleClickShowPassword}
										// onMouseDown={handleMouseDownPassword}
										edge="end"
									>
										{showPassword ? <VisibilityOff /> : <Visibility />}
									</IconButton>
								</InputAdornment>
							}
							// margin="normal"
							required
							name="password-confirmation"
							label="Confirm Password"
							type={showPassword ? 'text' : 'password'}
							id="password-confirmation"
							autoComplete="new-password"
							value={confirmPassword.value}
							onChange={confirmPasswordChangeHandler}
						/>
						<FormHelperText>
							{(touchedPassword && !confirmPassword.valid) ? confirmPassword.validationMessage : ""}
						</FormHelperText>
					</FormControl>

					{/*<TextField*/}
					{/*	margin="normal"*/}
					{/*	required*/}
					{/*	fullWidth*/}
					{/*	name="password-confirmation"*/}
					{/*	label="Confirm Password"*/}
					{/*	type="password"*/}
					{/*	id="password-confirmation"*/}
					{/*	autoComplete="new-password"*/}
					{/*	value={confirmPassword.value}*/}
					{/*	onChange={confirmPasswordChangeHandler}*/}
					{/*	helperText={(touchedPassword && !confirmPassword.valid) ? confirmPassword.validationMessage : ""}*/}
					{/*	error={touchedPassword && !confirmPassword.valid}*/}
					{/*/>*/}
					{/*<FormControlLabel*/}
					{/*	control={<Checkbox value="remember" color="primary"/>}*/}
					{/*	label="Remember me"*/}
					{/*/>*/}
					<FormControl error={true}>
						<FormGroup >
							<FormControlLabel control={<Checkbox value="remember" color="primary"
							                                     onChange={(event) => {
								                                     setTermsAccepted(event.target.checked);
							                                     }}
							/>}
							                  label={<>I agree to <a href="https://tigmat.io/legal/terms.html" target="_blank">the
								                  Terms &amp; Conditions</a></>}
							>

							</FormControlLabel>
						</FormGroup>
						{showTermsError && <FormHelperText>You must accept terms</FormHelperText>}
					</FormControl>

					<Button fullWidth variant="contained" sx={{mt: 3, mb: 2}} onClick={executeReCaptcha} disabled={registrationLoading}>
						Sign Up
					</Button>

					<Grid container direction="column" justifyContent="center" alignItems="flex-end">
						<Grid item>
							<Login/>
						</Grid>
						{/*<Grid item>*/}
						{/*	<NavLink to="/secret">Secret Page</NavLink>*/}
						{/*</Grid>*/}
					</Grid>
				</Box>
			</Box>
			<Copyright sx={{mt: 8, mb: 4}}/>
		</Container>
	);
};
export default SignUp;
