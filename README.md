# 🌿 ETo Predictor

An AI-powered Android app to predict **Reference Evapotranspiration (ETo)**
using the FAO-56 Penman-Monteith method.

## ✨ Features
- 📍 Auto-fetch weather data via GPS (Open-Meteo + OpenWeatherMap)
- ✏️ Manual input mode for all 6 weather parameters
- ⚡ Real-time ETo prediction via ML model API
- 🌙 Dark / Light theme toggle with neon UI
- 📊 Interactive result bottom sheet with irrigation advice

## 🔧 Parameters Used
| Parameter | Symbol | Unit |
|-----------|--------|------|
| Sunshine Hours | n | hrs |
| Max Temperature | Tmax | °C |
| Min Temperature | Tmin | °C |
| Max Relative Humidity | RHmax | % |
| Min Relative Humidity | RHmin | % |
| Wind Speed | u | m/s |

## 🛠️ Tech Stack
- **Android** (Kotlin)
- **Retrofit** — API calls
- **Open-Meteo API** — Weather data
- **OpenWeatherMap API** — Wind data
- **FAO-56 Penman-Monteith** — ETo calculation model
- **Material3** — Modern UI components

## 🚀 Backend
ML model hosted on Render:
`https://eto-predictor-1.onrender.com/predict`

## 📸 Screenshots
_Coming soon_

## 👨‍💻 Developer
**Rohil Hardik** — [@rohillahardik03](https://github.com/rohillahardik03)
