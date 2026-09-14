import "./globals.css";

export const metadata = {
  title: "AutoPlay",
  description: "Music automation dashboard"
};

export default function RootLayout({ children }) {
  return <html lang="en"><body>{children}</body></html>;
}
