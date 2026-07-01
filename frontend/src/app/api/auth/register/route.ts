import { NextResponse } from "next/server";
import { RegisterRequestBody, RegisterResponse, ApiResponse, BackendErrorResponse } from "@/types/auth";

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

    // Parse the response
    const result = await response.json();

    // Handle registration failures returned from backend
    if (!response.ok) {
      const errorResponse = result as BackendErrorResponse;
      let errorMessage = "Registration failed.";
      if (errorResponse.fieldErrors && errorResponse.fieldErrors.length > 0) {
        errorMessage = errorResponse.fieldErrors
          .map((f) => `${f.field}: ${f.message}`)
          .join(", ");
      } else if (errorResponse.message) {
        errorMessage = errorResponse.message;
      }
      return NextResponse.json(
        { error: errorMessage },
        { status: response.status }
      );
    }

    const apiResponse = result as ApiResponse<RegisterResponse>;
    return NextResponse.json({ success: true, data: apiResponse.data });
  } catch (error) {
    console.error("Register route handler error:", error);
    return NextResponse.json(
      { error: "Registration system is currently unavailable." },
      { status: 500 }
    );
  }
}
