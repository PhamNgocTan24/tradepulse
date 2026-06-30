import { NextResponse } from "next/server";
import { cookies } from "next/headers";

export async function POST() {
  try {
    const cookieStore = await cookies();
    const accessToken = cookieStore.get("access_token")?.value;

    // 1. Notify Backend Gateway to invalidate/blacklist this token in Redis
    if (accessToken) {
      const backendUrl = `${process.env.BACKEND_URL}/api/auth/logout`;
      
      try {
        await fetch(backendUrl, {
          method: "POST",
          headers: {
            "Authorization": `Bearer ${accessToken}`,
          },
        });
      } catch (backendError) {
        // Log the error but still proceed to clear local browser cookies
        console.error("Failed to notify backend logout:", backendError);
      }
    }

    // 2. Clear auth cookies from the browser
    cookieStore.delete("access_token");
    cookieStore.delete("refresh_token");

    return NextResponse.json({ success: true, message: "Logged out successfully" });
  } catch (error) {
    console.error("Logout route handler error:", error);
    return NextResponse.json(
      { error: "An error occurred during logout" },
      { status: 500 }
    );
  }
}
