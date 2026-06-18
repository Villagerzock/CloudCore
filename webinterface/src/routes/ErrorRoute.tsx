import {isRouteErrorResponse, useRouteError} from "react-router";
import type {JSX} from "react";

function ErrorRoute() : JSX.Element{
    const error = useRouteError();
    if (isRouteErrorResponse(error)){
        return (
            <>
                <h1>{error.status}</h1>
                <p>{error.statusText}</p>
            </>
        );
    }

    if (error instanceof Error) {
        return (
            <>
                <h1>Error</h1>
                <p>{error.message}</p>
            </>
        )
    }

    return (
        <>
            <h1>404</h1>
            <p>Not Found</p>
        </>
    )
}

export default ErrorRoute