import '@fontsource/roboto/300.css';
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/500.css';
import '@fontsource/roboto/700.css';

import Script from "next/script";

import Providers from './providers';
import {CONFIG} from "../config";

export default function RootLayout({children}) {
	return (
		<html lang="en">

		<head>
			<meta charSet="utf-8"/>
			<link rel="icon" href={`${CONFIG.BASE_PATH}/favicon.ico`}/>
			<meta name="viewport" content="width=device-width, initial-scale=1"/>
			<meta name="theme-color" content="#000000"/>
			<meta name="description" content="Nidam is starter project for Spring OAuth2 projects"/>
			<link rel="apple-touch-icon" href={`${CONFIG.BASE_PATH}/apple-touch-icon.png`}/>

			<link rel="manifest" href={`${CONFIG.BASE_PATH}/manifest.json`}/>

			<link rel="stylesheet" id="layout-styles-anchor" href={`${CONFIG.BASE_PATH}/splash-screen.css`}/>
			<link rel="stylesheet" id="layout-styles-anchor2" href={`${CONFIG.BASE_PATH}/spin.css`}/>
			<title>Nidam - By Mehdi Hafid</title>


			<Script src={`${CONFIG.BASE_PATH}/config.js`} strategy="beforeInteractive"/>
		</head>

		<body>
			<div id="splash-screen" className="splash-screen">
				<img src={`${CONFIG.BASE_PATH}/android-chrome-512x512.png`} alt="Nidam logo"/>
				<div className="loader">
					<div className="inner one"></div>
					<div className="inner two"></div>
					<div className="inner three"></div>
				</div>
			</div>

			<Providers>
				{children}
			</Providers>
		</body>
		</html>
	);
}