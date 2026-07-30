import React from "react";
import ReactDOM from "react-dom/client";
import { createBrowserRouter, RouterProvider } from "react-router-dom";
import "./index.css";

import Home from "./pages/Home.jsx";
import Login from "./pages/Login.jsx";
import EventOngoing from "./pages/EventOngoing.jsx";
import EventRecruiting from "./pages/EventRecruiting.jsx";
import BoothDetail from "./pages/BoothDetail.jsx";
import BoothApply from "./pages/BoothApply.jsx";
import MyPage from "./pages/MyPage.jsx";
import OrganizerAdmin from "./pages/OrganizerAdmin.jsx";
import PlatformAdmin from "./pages/PlatformAdmin.jsx";

const router = createBrowserRouter([
  { path: "/", element: <Home /> },
  { path: "/login", element: <Login /> },
  { path: "/event-ongoing", element: <EventOngoing /> },
  { path: "/event-recruiting", element: <EventRecruiting /> },
  { path: "/booth-detail", element: <BoothDetail /> },
  { path: "/booth-apply", element: <BoothApply /> },
  { path: "/mypage", element: <MyPage /> },
  { path: "/organizer-admin", element: <OrganizerAdmin /> },
  { path: "/platform-admin", element: <PlatformAdmin /> },
]);

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <RouterProvider router={router} />
  </React.StrictMode>
);
