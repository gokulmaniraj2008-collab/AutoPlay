import './globals.css';
import './white-theme.css';
import TommyChatDeleteEnhancer from './TommyChatDeleteEnhancer';

export const metadata = {
  title: 'Tommy AI',
  description: 'Tommy AI web control',
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>
        {children}
        <TommyChatDeleteEnhancer />
      </body>
    </html>
  );
}
