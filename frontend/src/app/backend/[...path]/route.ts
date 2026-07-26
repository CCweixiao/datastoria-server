import type { NextRequest } from "next/server";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const HOP_BY_HOP_HEADERS = new Set([
  "connection",
  "content-length",
  "host",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
]);

const proxy = async (
  request: NextRequest,
  context: { params: Promise<{ path: string[] }> }
): Promise<Response> => {
  const { path } = await context.params;
  const backendBase = (process.env.DATASTORIA_JAVA_INTERNAL_URL ?? "http://127.0.0.1:8080").replace(
    /\/+$/,
    ""
  );
  const target = new URL(`${backendBase}/${path.map(encodeURIComponent).join("/")}`);
  target.search = request.nextUrl.search;

  const requestHeaders = new Headers(request.headers);
  for (const header of HOP_BY_HOP_HEADERS) requestHeaders.delete(header);

  const hasBody = request.method !== "GET" && request.method !== "HEAD";
  const upstream = await fetch(target, {
    method: request.method,
    headers: requestHeaders,
    body: hasBody ? await request.arrayBuffer() : undefined,
    redirect: "manual",
    cache: "no-store",
  });

  const responseHeaders = new Headers(upstream.headers);
  for (const header of HOP_BY_HOP_HEADERS) responseHeaders.delete(header);
  // Node fetch transparently decodes compressed upstream bodies.
  responseHeaders.delete("content-encoding");

  return new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: responseHeaders,
  });
};

export const GET = proxy;
export const HEAD = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;
export const OPTIONS = proxy;
