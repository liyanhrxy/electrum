from abc import ABC, abstractmethod
from typing import Optional

from electrum_gui.common.provider.data import UnsignedTx


class ChainModelInterface(ABC):
    @abstractmethod
    def generate_unsigned_tx(
        self,
        wallet_id: int,
        coin_code: str,
        to_address: Optional[str] = None,
        value: Optional[int] = None,
        nonce: Optional[int] = None,
        fee_limit: Optional[int] = None,
        fee_price_per_unit: Optional[int] = None,
        payload: Optional[dict] = None,
    ) -> UnsignedTx:
        pass
