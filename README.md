# Orbital News & Market
<div align="center">
  <img src="orbital_logo.jpeg" alt="Orbital Logo" width="400" height="400"/>
</div>

> Orbital is a microservices-based platform that aggregates real-time cryptocurrency market data and financial news.

___

> [![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE) <br>
> [![GitHub forks](https://img.shields.io/github/forks/LoCh3f/orbital?style=social)](https://github.com/LoCh3f/orbital/network/members) <br>
> [![GitHub stars](https://img.shields.io/github/stars/LoCh3f/orbital?style=social)](https://github.com/LoCh3f/orbital/stargazers) <br>
> [![GitHub watchers](https://img.shields.io/github/watchers/LoCh3f/orbital?style=social)](https://github.com/LoCh3f/orbital/watchers) <br>


## 🚀 Features

- **Real-time Market Data** - Live cryptocurrency prices and metrics
- **News Aggregation** - Financial news from multiple sources  
- **Microservices Architecture** - Scalable and maintainable
- **RESTful APIs** - Clean, documented endpoints
- **Docker** - Containerized deployment
- **Monitoring** - Prometheus & Grafana integration


## 📁 Project Structure

orbital/ <br>
├── gateway/                 <br>
├── market-service/          <br>
├── news-service/            <br>
├── libs/                    <br>
│   ├── core/                <br>
│   └── models/              <br>
├── docker-compose.yml       <br>
└── monitoring/              <br>


## 🏗️ Architecture

```mermaid
graph TB
    Client[Client] --> Gateway[API Gateway]
    Gateway --> Market[Market Service]
    Gateway --> News[News Service]
    Market --> CoinGecko[CoinGecko API]
    Market --> PostgresM[(PostgreSQL)]
    Market --> Redis[(Redis)]
    News --> Redis
    News --> PostgresN[(PostgreSQL)]
    News --> NewsAPI[News API]
```

