import { BrowserRouter, Routes, Route } from 'react-router-dom';

/**
 * Root routing shell.
 * Routes will be added as we build each feature day by day.
 */
function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen bg-gray-50 font-sans">
        <Routes>
          {/* Temporary landing — replace on Day 2 with real HomePage */}
          <Route
            path="/"
            element={
              <div className="flex flex-col items-center justify-center min-h-screen">
                <div className="text-center">
                  <h1 className="text-4xl font-bold text-primary-600 mb-2">
                    🏏 Turfzy
                  </h1>
                  <p className="text-gray-500 text-lg">
                    Sports Turf Booking Platform
                  </p>
                  <span className="mt-4 inline-block px-3 py-1 bg-green-100 text-green-700 rounded-full text-sm font-medium">
                    Day 1 — Setup Complete ✅
                  </span>
                </div>
              </div>
            }
          />
        </Routes>
      </div>
    </BrowserRouter>
  );
}

export default App;