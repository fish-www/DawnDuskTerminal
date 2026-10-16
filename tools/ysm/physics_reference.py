"""量化二级替换（滤波器 -> 它的输入）的代价

下面两个公式是 OpenYSM（MIT）的参考实现，OpenYSM 是 Yes Steve Model 的开放重实现：

  first_order(name, input, response):
      last = (1 - dt/response) * last + (dt/response) * input

  second_order(name, input, frequency, coefficient, response):
      frequency  = clamp(frequency, 0, 5)
      coefficient = clamp(coefficient, 0, 1)
      k1 = coefficient / pi / frequency
      k2 = 1 / ((2*pi*frequency)^2)
      k3 = response * coefficient / 2 / pi / frequency
      按 dt 分子步积分
          x' = x + dt*v
          v' = v + dt * (k3*inputDot + input - x - k1*v) / k2
      value = x

dt 是 (renderTicks - lastRenderTicks) / 20，也就是 20 fps 下的一个 tick

这个脚本把两个滤波器分别喂单位阶跃和常量，于是报告能给出替换实际引入的稳态误差，而不是含糊带过
"""
from __future__ import annotations

import math

DT = 1.0 / 20.0


def first_order_state(input_value: float, response: float, state: float) -> float:
    return (1 - (DT / response)) * state + (DT / response) * input_value


def second_order_state(input_value, frequency, coefficient, response, state, state_dot, input_prev):
    frequency = min(max(frequency, 0.0), 5.0)
    coefficient = min(max(coefficient, 0.0), 1.0)
    k1 = coefficient / math.pi / frequency
    k2 = 1.0 / (2 * math.pi * frequency) / (2 * math.pi * frequency)
    k3 = response * coefficient / 2 / math.pi / frequency

    input_dot = (input_value - input_prev) / DT
    max_dt = math.sqrt(4 * k2 + k1 * k1) - k1
    cycles = max(1, math.ceil(DT / max_dt)) if max_dt > 0 else 1
    step = DT / cycles

    for _ in range(cycles):
        state = state + step * state_dot
        state_dot = state_dot + step * (k3 * input_dot + input_value - state - k1 * state_dot) / k2
    return state, state_dot, input_value


def settle(frequency, coefficient, response, target=1.0, ticks=200):
    """把滤波器跑到收敛，返回最终值以及到达目标的 90% 所用的 tick 数"""
    state = state_dot = input_prev = 0.0
    reached = None
    for tick in range(1, ticks + 1):
        state, state_dot, input_prev = second_order_state(
            target, frequency, coefficient, response, state, state_dot, input_prev)
        if reached is None and target and abs(state) >= 0.9 * abs(target):
            reached = tick
    return state, reached


def main():
    print("second_order 对单位阶跃的稳态增益与 90% 稳定时间")
    print(f"{'freq':>5}{'coef':>6}{'resp':>6}{'gain':>10}{'90% at':>9}")
    for frequency, coefficient, response in [
        (1.0, 0.8, 0.0),
        (2.0, 0.4, 0.0),
        (2.0, 0.4, 1.0),
        (2.5, 0.3, 0.0),
        (3.0, 0.3, 0.0),
        (3.0, 0.3, 1.0),
    ]:
        gain, reached = settle(frequency, coefficient, response)
        reached_text = f"{reached} ticks" if reached else ">200"
        print(f"{frequency:>5}{coefficient:>6}{response:>6}{gain:>10.4f}{reached_text:>9}")

    print()
    print("first_order 对单位阶跃的稳态增益")
    print(f"{'response':>9}{'gain':>10}{'90% at':>9}")
    for response in (0.3, 0.5):
        state = 0.0
        reached = None
        for tick in range(1, 600 + 1):
            state = first_order_state(1.0, response, state)
            if reached is None and abs(state) >= 0.9:
                reached = tick
        print(f"{response:>9}{state:>10.4f}{str(reached) + ' ticks':>9}")

    print()
    print("解读：增益 1.0 表示滤波器收敛到喂进去的那个值，所以把调用换成它的输入")
    print("在稳态上是精确的，只丢掉了滞后和可能出现的过冲 —— 那正是稳定时间那一列量度出来的东西")


if __name__ == "__main__":
    main()
