# Frontend Developer Guidelines for Aux

## 1. Styling & UI Standards
- **Read the Style Guide:** Before creating or modifying any UI components, you must parse and follow `./STYLE_GUIDE.html`.
- **CSS Formatting:** Write custom CSS. Alphabetize all CSS properties and format them on a single line per selector.
- **Framework Restrictions:** Do not use Tailwind CSS or similar utility classes.
- **Design Language:** All design language must follow the style guide. If something is unclear or undocumented, ask me questions until the direction is clear. Do not guess.

## 2. API Contracts & Backend Sync
- **Check the Docs:** Before integrating any API calls, you must review the OpenAPI documentation at `../backend/docs/index.html` to confirm exact payload and response shapes.
- **Endpoint Descriptions:** Pay close attention to the descriptions for each endpoint. The backend developer (Justin) leaves ideas, edge-case warnings, or questions in these descriptions. If you spot a question or note, pause and ask the frontend developer how they want to handle it before writing the implementation.

## 3. Handling Missing Backend Features (Frontend > Backend)
If the frontend requires data, endpoints, or structure updates that are not yet documented in `../backend/docs/index.html`:
1. Do not hallucinate or guess the final API shape.
2. Mock the data locally to unblock the frontend UI work.
3. Open or append to `./BACKEND_REQUESTS.md`.
4. Document the exact HTTP method, required route, expected request payload, and desired response shape so the backend developer knows exactly what to build.