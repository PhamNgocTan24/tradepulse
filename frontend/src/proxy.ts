import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

export function proxy(request: NextRequest) {
  // 1. Retrieve the access token from HTTP cookies
  const token = request.cookies.get("access_token")?.value;
  const { pathname } = request.nextUrl;

  // Define paths that require an authenticated session
  const isProtectedRoute = 
    pathname.startsWith("/trade") || 
    pathname.startsWith("/portfolio") || 
    pathname.startsWith("/dashboard");

  const isAuthRoute = pathname === "/login";

  // Case 1: Unauthenticated request targeting a protected route -> redirect to login page
  if (isProtectedRoute && !token) {
    const loginUrl = new URL("/login", request.url);
    
    // Store target path in query param (e.g. /login?callback=/trade) for post-login redirect
    loginUrl.searchParams.set("callback", pathname);
    
    return NextResponse.redirect(loginUrl);
  }

  // Case 2: Authenticated user attempting to access login page -> redirect to trade page
  if (isAuthRoute && token) {
    return NextResponse.redirect(new URL("/trade", request.url));
  }

  // Allow the request to proceed to the destination route
  return NextResponse.next();
}

// Configuration defining which routes should trigger this middleware
export const config = {
  matcher: [
    "/trade/:path*", 
    "/portfolio/:path*", 
    "/dashboard/:path*", 
    "/login"
  ],
};
