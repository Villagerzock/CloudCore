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

function createRouter(){
    return createBrowserRouter([
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
                    path:"/server/:id",
                    element: <ServerRoute/>
                },
                {
                    path:"/templates",
                    element: <TemplateListRoute/>
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
    <RouterProvider router={router}/>
)
