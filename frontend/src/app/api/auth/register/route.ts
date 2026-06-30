import { NextResponse } from "next/server";
import { RegisterRequestBody, RegisterResponse, ApiResponse } from "@/types/auth";

export async function POST(request: Request) {
  try {
    // Parse the JSON request body and cast to RegisterRequestBody
    const body = (await request.json()) as RegisterRequestBody;
    const { email, password, displayName } = body;

    // Call the Backend API Gateway
    const backendUrl = `${process.env.BACKEND_URL}/api/auth/register`;
    const response = await fetch(backendUrl, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        email,
        password,
        displayName,
      }),
    });

    // Parse the response as ApiResponse wrapping RegisterResponse
    const result = (await response.json()) as ApiResponse<RegisterResponse>;

    // Handle registration failures returned from backend
    if (!result.success || !result.data) {
      return NextResponse.json(
        { error: result.message || "Registration failed." },
        { status: response.status }
      );
    }

    return NextResponse.json({ success: true, data: result.data });
  } catch (error) {
    console.error("Register route handler error:", error);
    return NextResponse.json(
      { error: "Registration system is currently unavailable." },
      { status: 500 }
    );
  }
}
