import { NextResponse } from "next/server";
import { cookies } from "next/headers";
import { LoginRequestBody, AuthResponse, ApiResponse } from "@/types/auth";

export async function POST(request: Request) {
  try {
    // Parse the JSON request body and cast to LoginRequestBody
    const body = (await request.json()) as LoginRequestBody;
    const { email, password, totpCode } = body;

    // Call the Backend API Gateway
    const backendUrl = `${process.env.BACKEND_URL}/api/auth/login`;
    const response = await fetch(backendUrl, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        email,
        password,
        totpCode,
      }),
    });

    // Parse the response as ApiResponse wrapping AuthResponse
    const result = (await response.json()) as ApiResponse<AuthResponse>;

    // Handle authentication failures returned from backend
    if (!result.success || !result.data) {
      return NextResponse.json(
        { error: result.message || "Invalid credentials." },
        { status: response.status }
      );
    }

    const { accessToken, refreshToken, accessTokenExpiresIn } = result.data;
    const cookieStore = await cookies();

    // Store Access Token in an HttpOnly cookie (valid for 15 minutes)
    cookieStore.set("access_token", accessToken, {
      httpOnly: true, // Prevents client-side JS from accessing the token (XSS protection)
      secure: process.env.NODE_ENV === "production", // HTTPS only in production
      sameSite: "lax", // Protects against CSRF attacks
      path: "/",
      maxAge: accessTokenExpiresIn,
    });

    // Store Refresh Token in an HttpOnly cookie (valid for 7 days)
    if (refreshToken) {
      cookieStore.set("refresh_token", refreshToken, {
        httpOnly: true,
        secure: process.env.NODE_ENV === "production",
        sameSite: "lax",
        path: "/",
        maxAge: 7 * 24 * 3600,
      });
    }

    return NextResponse.json({ success: true });
  } catch (error) {
    console.error("Login route handler error:", error);
    return NextResponse.json(
      { error: "Authentication system is currently unavailable." },
      { status: 500 }
    );
  }
}