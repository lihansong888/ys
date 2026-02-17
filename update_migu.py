import requests
import re
import time

def get_real_url(cid):
    # 直接模拟移动端请求，不再经过 fanmingming 接口
    url = f"https://m.miguvideo.com/mgs/msite/prd/detail.html?cid={cid}"
    headers = {
        "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 14_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0.3 Mobile/15E148 Safari/104.1"
    }
    # 这里我们直接拼接目前最稳的咪咕大网源地址
    return f"http://gdovp.v.tongbu.com/migu/live/{cid}/playlist.m3u8"

def update():
    channels = [
        ("CCTV-1", "608807420"),
        ("CCTV-5", "608807428"),
        ("CCTV-5+", "608807435"),
        ("江苏卫视", "608807446"),
        ("湖南卫视", "608807447"),
        ("浙江卫视", "608807448")
    ]
    
    output = ["咪咕直播,#genre#"]
    for name, cid in channels:
        real_url = get_real_url(cid)
        output.append(f"{name},{real_url}")
    
    with open("migu.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(output))

if __name__ == "__main__":
    update()
    print("咪咕原生流更新完成")
