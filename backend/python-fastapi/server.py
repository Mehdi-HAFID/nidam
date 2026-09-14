from typing import Annotated, Optional

import jwt
from fastapi import Depends, FastAPI, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jwt import PyJWKClient

app = FastAPI()

PORT = 4003

ISSUER = "http://localhost:7080/auth"
AUDIENCE = "client"

JWKS_URL = f"{ISSUER}/oauth2/jwks"

jwks_client = PyJWKClient(JWKS_URL)

bearer_scheme = HTTPBearer(auto_error=False)


class Authentication:
    """
    Equivalent, conceptually, to Spring Security's
    JwtAuthenticationToken.
    """

    def __init__(self, token: str, claims: dict):
        self.token = token
        self.claims = claims

        authorities = claims.get("authorities", [])

        self.authorities = (
            authorities
            if isinstance(authorities, list)
            else []
        )

        self.name = claims.get("sub")


def authenticate(
    credentials: Annotated[
        Optional[HTTPAuthorizationCredentials],
        Depends(bearer_scheme)
    ]
) -> Optional[Authentication]:

    # No Authorization header:
    # behave like Spring's anonymous Authentication.
    if credentials is None:
        return None

    token = credentials.credentials

    try:
        signing_key = jwks_client.get_signing_key_from_jwt(token)

        claims = jwt.decode(
            token,
            signing_key.key,
            algorithms=["RS256"],
            issuer=ISSUER,
            audience=AUDIENCE,
        )

        return Authentication(token, claims)

    except jwt.PyJWTError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid access token",
        )


def require_authentication(
    authentication: Annotated[
        Optional[Authentication],
        Depends(authenticate)
    ]
) -> Authentication:

    if authentication is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication required",
        )

    return authentication


def require_authority(*required_authorities):
    def dependency(
        authentication: Annotated[
            Authentication,
            Depends(require_authentication)
        ]
    ) -> Authentication:

        allowed = any(
            authority in authentication.authorities
            for authority in required_authorities
        )

        if not allowed:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Forbidden",
            )

        return authentication

    return dependency


@app.get("/me")
def get_me(
    authentication: Annotated[
        Optional[Authentication],
        Depends(authenticate)
    ]
):
    if authentication is None:
        return {
            "username": "",
            "email": "",
            "authorities": [],
            "exp": 9223372036854775807,
        }

    claims = authentication.claims

    exp = claims.get(
        "exp",
        9223372036854775807
    )

    return {
        "username": authentication.name or "",
        "email": claims.get("email", ""),
        "authorities": authentication.authorities,
        "exp": exp,
    }


@app.get("/demo")
def allowed_resource(
    authentication: Annotated[
        Authentication,
        Depends(
            require_authority(
                "manage-users",
                "manage-projects"
            )
        )
    ]
):
    return {
        "token": authentication.token,
        "claims": authentication.claims,
        "authorities": authentication.authorities,
        "name": authentication.name,
    }


@app.get("/top-secret")
def forbidden_resource(
    _: Annotated[
        Authentication,
        Depends(
            require_authority("top-secret")
        )
    ]
):
    return "Top secret information"


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "server:app",
        host="localhost",
        port=PORT,
        reload=False,
    )