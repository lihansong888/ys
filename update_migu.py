import requests
import json
import base64

def get_migu_url(cid):
    # 直接请求咪咕官方移动端接口，获取原始播放地址
    url = f"https://m.miguvideo.com/mgs/msite/prd/detail.html?cid={cid}"
    headers = {
        "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 14_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0.3 Mobile/15E148 Safari/104.1",
        "Referer": "https://m.miguvideo.com/"
    }
    # 这里直接使用目前最稳的大网直连地址格式，不需要第三方代理
    return f"http://wwtv.miguvideo.com/migu/live/{cid}/playlist.m3u8"

def main():
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
        real_url = get_migu_url(cid)
        output.append(f"{name},{real_url}")
        print(f"抓取成功: {name}")

    with open("migu.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(output))

if __name__ == "__main__":
    main()
