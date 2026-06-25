import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import {createBrowserRouter, RouterProvider} from "react-router";
import DashboardRoute from "./routes/DashboardRoute.tsx";
import ErrorRoute from "./routes/ErrorRoute.tsx";
import ProxyRoute from "./routes/ProxyRoute.tsx";
import ServerListRoute from "./routes/ServerListRoute.tsx";
import ServerRoute from "./routes/ServerRoute.tsx";
import TemplateListRoute from "./routes/TemplateListRoute.tsx";
import LoginRoute from "./routes/LoginRoute.tsx";
import RegisterRoute from "./routes/RegisterRoute.tsx";
import NodeListRoute from "./routes/NodeListRoute.tsx";
import BackgroundEffect from "./components/BackgroundEffect.tsx";
import UserListRoute from "./routes/UserListRoute.tsx";

function createRouter(){
    return createBrowserRouter([
        {
            path: "/login",
            element: <LoginRoute/>
        },
        {
            path: "/register",
            element: <RegisterRoute/>
        },
        {
            path: "/nodes",
            element: <NodeListRoute/>
        },
        {
            path:"/",
            element: <App/>,
            children: [
                {
                    index: true,
                    element: <DashboardRoute/>
                },
                {
                    path:"/proxy",
                    element: <ProxyRoute/>
                },
                {
                    path:"/servers",
                    element: <ServerListRoute/>
                },
                {
                    path:"/server/:name",
                    element: <ServerRoute/>
                },
                {
                    path:"/templates",
                    element: <TemplateListRoute/>
                },
                {
                    path:"/users",
                    element: <UserListRoute/>
                },
                {
                    path:"*",
                    errorElement:<ErrorRoute/>,
                    element: <ErrorRoute/>
                }
            ]
        }
    ])
}

const router = createRouter();



createRoot(document.getElementById('root')!).render(
    <>
        <BackgroundEffect/>
        <RouterProvider router={router}/>
    </>
)
