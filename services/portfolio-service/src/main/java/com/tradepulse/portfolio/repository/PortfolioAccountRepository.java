package com.tradepulse.portfolio.repository;

import com.tradepulse.portfolio.domain.entity.PortfolioAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PortfolioAccountRepository extends JpaRepository<PortfolioAccount, UUID> {}
