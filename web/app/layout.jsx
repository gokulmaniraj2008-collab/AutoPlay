import './globals.css';
import './white-theme.css';

export const metadata = {
  title: 'Tommy AI',
  description: 'Tommy AI web control',
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
